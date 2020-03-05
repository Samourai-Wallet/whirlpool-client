package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import io.reactivex.Observable;
import io.reactivex.subjects.Subject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java8.util.Optional;
import java8.util.function.Function;
import java8.util.function.Predicate;
import java8.util.stream.Collectors;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MixOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(MixOrchestrator.class);
  private static final int LAST_ERROR_DELAY = 60 * 5; // 5min
  private static final int MIX_MIN_CONFIRMATIONS = 1;

  private MixingStateEditable mixingState;
  private Integer maxClients;
  private int maxClientsPerPool;
  private boolean autoMix;
  private int mixsTargetMin;

  private ConcurrentHashMap<String, Mixing> mixing;
  private Set<String> mixingHashs;
  private Map<String, Integer> mixingPerPool;

  public MixOrchestrator(
      int loopDelay,
      int clientDelay,
      MixingStateEditable mixingState,
      Integer maxClients,
      int maxClientsPerPool,
      boolean autoMix,
      int mixsTargetMin) {
    super(loopDelay, 0, clientDelay);

    this.mixingState = mixingState;
    this.maxClients = maxClients;
    this.maxClientsPerPool = maxClientsPerPool;
    this.autoMix = autoMix;
    this.mixsTargetMin = mixsTargetMin;
  }

  protected abstract Stream<WhirlpoolUtxo> getQueue();

  protected abstract Collection<Pool> getPools() throws Exception;

  protected abstract WhirlpoolClient runWhirlpoolClient(
      WhirlpoolUtxo whirlpoolUtxo, WhirlpoolClientListener listener) throws NotifiableException;

  protected void stopWhirlpoolClient(Mixing mixing, boolean cancel, boolean reQueue) {
    if (log.isDebugEnabled()) {
      String reQueueStr = reQueue ? "(REQUEUE)" : "";
      if (cancel) {
        log.debug("Canceling mixing client" + reQueueStr + ": " + mixing);
      } else {
        log.debug("Stopping mixing client" + reQueueStr + ": " + mixing);
      }
    }
    // override here
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
    this.mixing = new ConcurrentHashMap<String, Mixing>();
    this.mixingHashs = new HashSet<String>();
    this.mixingPerPool = new HashMap<String, Integer>();
  }

  @Override
  protected void runOrchestrator() {
    try {
      findAndMix();
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected boolean findAndMix() throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("checking for queued utxos to mix...");
    }

    // find mixable for each pool
    boolean found = false;
    for (Pool pool : getPools()) {
      try {
        boolean foundForPool = findAndMix(pool.getPoolId());
        if (foundForPool) {
          found = true;
        }
      } catch (Exception e) {
        log.error("", e);
      }
    }
    return found;
  }

  @Override
  public synchronized void stop() {
    super.stop();
    stopMixingClients();
  }

  public synchronized void stopMixingClients() {
    for (Mixing oneMixing : mixing.values()) {
      stopWhirlpoolClient(oneMixing, true, false);
    }
    mixing.clear();
    mixingHashs.clear();
    mixingPerPool.clear();
    mixingState.setUtxosMixing(computeUtxosMixing());
  }

  private synchronized boolean findAndMix(String poolId) throws Exception {
    if (!isStarted()) {
      return false; // wallet stopped in meantime
    }

    // find mixable for pool
    WhirlpoolUtxo[] mixableUtxos = findMixable(poolId);
    if (mixableUtxos == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "["
                + poolId
                + "] "
                + getNbMixing(poolId)
                + " mixing, no additional queued utxo mixable now");
      }
      return false;
    }

    // mix
    WhirlpoolUtxo whirlpoolUtxo = mixableUtxos[0];
    WhirlpoolUtxo mixingToSwap = mixableUtxos[1]; // may be null
    mix(whirlpoolUtxo, mixingToSwap);
    setLastRun();
    return true;
  }

  private Collection<WhirlpoolUtxo> computeUtxosMixing() {
    return StreamSupport.stream(mixing.values())
        .map(
            new Function<Mixing, WhirlpoolUtxo>() {
              @Override
              public WhirlpoolUtxo apply(Mixing m) {
                return m.getUtxo();
              }
            })
        .collect(Collectors.<WhirlpoolUtxo>toList());
  }

  private Optional<Mixing> findMixingToSwap(
      final WhirlpoolUtxo toMix,
      final String mixingHashCriteria,
      final boolean bestPriorityCriteria) {
    final WhirlpoolUtxoPriorityComparator comparator =
        WhirlpoolUtxoPriorityComparator.getInstance();
    return StreamSupport.stream(mixing.values())
        .filter(
            new Predicate<Mixing>() {
              @Override
              public boolean test(Mixing mixing) {
                // should not interrupt a mix
                MixProgress mixProgress = mixing.getUtxo().getUtxoState().getMixProgress();
                if (mixProgress != null && !mixProgress.getMixStep().isInterruptable()) {
                  return false;
                }

                if (mixingHashCriteria != null) {
                  String mixingHash = mixing.getUtxo().getUtxo().tx_hash;
                  if (!mixingHash.equals(mixingHashCriteria)) {
                    return false;
                  }
                }

                // should be lower priority
                if (bestPriorityCriteria && comparator.compare(mixing.getUtxo(), toMix) <= 0) {
                  return false;
                }
                return true;
              }
            })
        .findFirst();
  }

  public boolean hasMoreMixableOrUnconfirmed() {
    List<WhirlpoolUtxo> unconfirmedUtxos =
        getQueueByMixableStatus(false, null, MixableStatus.MIXABLE, MixableStatus.UNCONFIRMED);
    return !unconfirmedUtxos.isEmpty();
  }

  public boolean hasMoreMixingThreadAvailable(String poolId) {
    // check maxClients
    if (maxClients != null && mixing.size() >= maxClients) {
      return false;
    }

    // check maxClientsPerPool
    int nbMixingInPool = getNbMixing(poolId);
    if (nbMixingInPool >= maxClientsPerPool) {
      return false;
    }
    return true;
  }

  private int getNbMixing(String poolId) {
    Integer nbMixingInPool = mixingPerPool.get(poolId);
    return (nbMixingInPool != null ? nbMixingInPool : 0);
  }

  private boolean isHashMixing(String txid) {
    return mixingHashs.contains(txid);
  }

  protected Mixing getMixing(UnspentResponse.UnspentOutput utxo) {
    final String key = ClientUtils.utxoToKey(utxo);
    return mixing.get(key);
  }

  // returns [mixable,mixingToSwapOrNull]
  private WhirlpoolUtxo[] findMixable(final String poolId) {
    Predicate<WhirlpoolUtxo> filter =
        new Predicate<WhirlpoolUtxo>() {
          @Override
          public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
            // filter by poolId
            if (!poolId.equals(whirlpoolUtxo.getUtxoConfig().getPoolId())) {
              return false;
            }
            return true;
          }
        };
    List<WhirlpoolUtxo> mixableUtxos = getQueueByMixableStatus(true, filter, MixableStatus.MIXABLE);

    // find first mixable utxo, eventually by swapping a lower priority mixing utxo
    for (WhirlpoolUtxo toMix : mixableUtxos) {
      WhirlpoolUtxo[] swap = findSwap(toMix, false);
      if (swap != null) {
        return swap;
      }
    }
    // no mixable found
    return null;
  }

  private WhirlpoolUtxo[] findSwap(WhirlpoolUtxo toMix, boolean mixNow) {
    String toMixHash = toMix.getUtxo().tx_hash;
    final String mixingHashCriteria = isHashMixing(toMixHash) ? toMixHash : null;
    String poolId = toMix.getUtxoConfig().getPoolId();
    if (mixingHashCriteria == null && hasMoreMixingThreadAvailable(poolId)) {
      // no swap required
      return new WhirlpoolUtxo[] {toMix, null};
    }

    // a swap is required to mix this utxo
    boolean bestPriorityCriteria = !mixNow;
    Optional<Mixing> mixingToSwapOpt =
        findMixingToSwap(toMix, mixingHashCriteria, bestPriorityCriteria);
    if (mixingToSwapOpt.isPresent()) {
      // found mixing to swap
      return new WhirlpoolUtxo[] {toMix, mixingToSwapOpt.get().getUtxo()};
    }
    return null;
  }

  private Map<String, Integer> computeMixingPerPool() {
    Map<String, Integer> mixingPerPool = new HashMap<String, Integer>();
    for (Mixing mixingItem : mixing.values()) {
      String poolId = mixingItem.getPoolId();
      int currentCount = getNbMixing(poolId);
      mixingPerPool.put(poolId, currentCount + 1);
    }
    return mixingPerPool;
  }

  private List<WhirlpoolUtxo> getQueueByMixableStatus(
      final boolean filterErrorDelay,
      Predicate<WhirlpoolUtxo> utxosFilter,
      final MixableStatus... filterMixableStatuses) {
    final long lastErrorMax = System.currentTimeMillis() - (LAST_ERROR_DELAY * 1000);

    // find queued
    Stream<WhirlpoolUtxo> stream =
        getQueue()
            .filter(
                new Predicate<WhirlpoolUtxo>() {
                  @Override
                  public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                    WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
                    // don't retry before errorDelay
                    boolean accepted =
                        (!filterErrorDelay
                            || utxoState.getLastError() == null
                            || utxoState.getLastError() < lastErrorMax);
                    if (!accepted) {
                      return false;
                    }

                    // filter by mixableStatus
                    return ArrayUtils.contains(filterMixableStatuses, utxoState.getMixableStatus());
                  }
                });
    if (utxosFilter != null) {
      stream = stream.filter(utxosFilter);
    }
    List<WhirlpoolUtxo> whirlpoolUtxos = stream.collect(Collectors.<WhirlpoolUtxo>toList());

    // suffle & sort
    sortShuffled(whirlpoolUtxos);
    return whirlpoolUtxos;
  }

  protected void sortShuffled(List<WhirlpoolUtxo> whirlpoolUtxos) {
    // shuffle
    Collections.shuffle(whirlpoolUtxos);

    // sort by priority, but keep utxos shuffled when same-priority
    Collections.sort(whirlpoolUtxos, WhirlpoolUtxoPriorityComparator.getInstance());
  }

  private MixableStatus computeMixableStatus(WhirlpoolUtxo whirlpoolUtxo) {

    // check pool
    if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
      return MixableStatus.NO_POOL;
    }

    // check confirmations
    if (whirlpoolUtxo.getUtxo().confirmations < MIX_MIN_CONFIRMATIONS) {
      return MixableStatus.UNCONFIRMED;
    }

    // ok
    return MixableStatus.MIXABLE;
  }

  private void refreshMixableStatus(WhirlpoolUtxo whirlpoolUtxo) {
    boolean wasMixable =
        MixableStatus.MIXABLE.equals(whirlpoolUtxo.getUtxoState().getMixableStatus());
    ;

    MixableStatus mixableStatus = computeMixableStatus(whirlpoolUtxo);
    whirlpoolUtxo.getUtxoState().setMixableStatus(mixableStatus);

    boolean isMixable = MixableStatus.MIXABLE.equals(mixableStatus);

    if (log.isTraceEnabled()) {
      log.trace("refreshMixableStatus: " + wasMixable + " -> " + isMixable + " : " + whirlpoolUtxo);
    }
    if (wasMixable != isMixable) {
      onMixableStatusChange(whirlpoolUtxo, wasMixable, isMixable);
    }
  }

  public void mixQueue(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
    WhirlpoolUtxoStatus utxoStatus = utxoState.getStatus();
    if (WhirlpoolUtxoStatus.MIX_QUEUE.equals(utxoStatus)) {
      // already queued
      log.warn("mixQueue ignored: utxo already queued for " + whirlpoolUtxo);
      return;
    }
    if (getMixing(whirlpoolUtxo.getUtxo()) != null
        || WhirlpoolUtxoStatus.MIX_SUCCESS.equals(utxoStatus)) {
      log.warn("mixQueue ignored: utxo already mixing for " + whirlpoolUtxo);
      return;
    }
    if (!WhirlpoolUtxoStatus.MIX_FAILED.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.READY.equals(utxoStatus)) {
      throw new NotifiableException(
          "cannot add to mix queue: utxoStatus=" + utxoStatus + " for " + whirlpoolUtxo);
    }
    if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
      throw new NotifiableException("cannot add to mix queue: no pool set for " + whirlpoolUtxo);
    }

    // add to queue
    utxoState.setStatus(WhirlpoolUtxoStatus.MIX_QUEUE, false);
    if (log.isDebugEnabled()) {
      log.debug(" + mixQueue: " + whirlpoolUtxo);
    }
    mixingState.incrementUtxoQueued(whirlpoolUtxo);
    notifyOrchestrator();
  }

  public Observable<MixProgress> mixNow(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    // verify & queue
    mixQueue(whirlpoolUtxo);

    // mix now
    WhirlpoolUtxo[] mixableUtxos = findSwap(whirlpoolUtxo, true);
    if (mixableUtxos == null) {
      log.warn("No thread available to mix now, mix queued: " + whirlpoolUtxo);
      return null;
    }
    WhirlpoolUtxo mixingToSwap = mixableUtxos[1]; // may be null
    return mix(whirlpoolUtxo, mixingToSwap);
  }

  public synchronized void mixStop(WhirlpoolUtxo whirlpoolUtxo, boolean cancel, boolean reQueue) {
    WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();

    final String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    Mixing myMixing = mixing.get(key);
    if (myMixing != null) {
      // stop mixing
      stopWhirlpoolClient(myMixing, cancel, reQueue);
    } else {
      // remove from queue
      if (cancel) {
        log.debug("Remove from queue: " + whirlpoolUtxo);
      }
      boolean wasQueued = WhirlpoolUtxoStatus.MIX_QUEUE.equals(utxoState.getStatus());
      WhirlpoolUtxoStatus utxoStatus =
          cancel ? WhirlpoolUtxoStatus.READY : WhirlpoolUtxoStatus.STOP;
      utxoState.setStatus(utxoStatus, false);

      // recount QUEUE if it was queued
      if (wasQueued) {
        mixingState.setUtxosQueued(getQueue().collect(Collectors.<WhirlpoolUtxo>toList()));
      }
    }
  }

  protected synchronized Observable<MixProgress> mix(
      WhirlpoolUtxo whirlpoolUtxo, WhirlpoolUtxo mixingToSwap) throws NotifiableException {
    if (!isStarted()) {
      throw new NotifiableException("Wallet is stopped");
    }

    if (log.isDebugEnabled()) {
      log.debug(
          " + Mix("
              + (mixingToSwap != null ? "SWAP" : "IDLE")
              + "): "
              + whirlpoolUtxo
              + " ; "
              + whirlpoolUtxo.getUtxoConfig());
    }
    if (mixingToSwap != null) {
      // stop mixingToSwap
      mixStop(mixingToSwap, true, true);
    }

    // check mixable
    MixableStatus mixableStatus = whirlpoolUtxo.getUtxoState().getMixableStatus();
    if (!MixableStatus.MIXABLE.equals(mixableStatus)) {
      throw new NotifiableException("Cannot mix: " + mixableStatus);
    }

    // mix
    MixProgress mixProgress = new MixProgress(MixStep.CONNECTING);
    whirlpoolUtxo.getUtxoState().setStatus(WhirlpoolUtxoStatus.MIX_STARTED, true, mixProgress);

    // run mix
    WhirlpoolClientListener listener = computeMixListener(whirlpoolUtxo);
    WhirlpoolClient whirlpoolClient = runWhirlpoolClient(whirlpoolUtxo, listener);
    Subject<MixProgress> observable = listener.getObservable();
    Mixing mixing =
        new Mixing(
            whirlpoolUtxo, whirlpoolUtxo.getUtxoConfig().getPoolId(), whirlpoolClient, observable);
    addMixing(mixing);
    return observable;
  }

  private WhirlpoolClientListener computeMixListener(final WhirlpoolUtxo whirlpoolUtxo) {
    return new LoggingWhirlpoolClientListener(whirlpoolUtxo.getUtxoConfig().getPoolId()) {
      @Override
      public void success(MixSuccess mixSuccess) {
        super.success(mixSuccess);
        MixProgressSuccess mixProgress =
            new MixProgressSuccess(mixSuccess.getReceiveAddress(), mixSuccess.getReceiveUtxo());

        // update utxo
        WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
        utxoState.setStatus(WhirlpoolUtxoStatus.MIX_SUCCESS, true, mixProgress);
        whirlpoolUtxo.getUtxoConfig().incrementMixsDone();

        // manage
        removeMixing(whirlpoolUtxo);
        onMixSuccess(whirlpoolUtxo, mixSuccess);

        // notify mixProgress
        getObservable().onNext(mixProgress);
        getObservable().onComplete();

        // idle => notify orchestrator
        notifyOrchestrator();
      }

      @Override
      public void fail(MixFailReason reason, String notifiableError) {
        super.fail(reason, notifiableError);
        MixProgress mixProgress = new MixProgressFail(reason);

        // update utxo
        String message = reason.getMessage();
        if (notifiableError != null) {
          message += " ; " + notifiableError;
        }
        WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
        if (reason == MixFailReason.STOP) {
          utxoState.setStatus(WhirlpoolUtxoStatus.STOP, false, mixProgress);
          utxoState.setError(message);
        } else if (reason == MixFailReason.CANCEL) {
          // silent stop
          utxoState.setStatus(WhirlpoolUtxoStatus.READY, false, mixProgress);
        } else {
          utxoState.setStatus(WhirlpoolUtxoStatus.MIX_FAILED, true, mixProgress);
          utxoState.setError(message);
        }

        // manage
        removeMixing(whirlpoolUtxo);
        onMixFail(whirlpoolUtxo, reason, notifiableError);

        // notify mixProgress
        getObservable().onNext(mixProgress);
        getObservable().onComplete();

        // idle => notify orchestrator
        notifyOrchestrator();
      }

      @Override
      public void progress(MixStep step) {
        super.progress(step);
        MixProgress mixProgress = new MixProgress(step);

        // update utxo
        WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
        utxoState.setMessage(step.getMessage());
        utxoState.setStatus(utxoState.getStatus(), true, mixProgress);

        // notify mixProgress
        getObservable().onNext(mixProgress);
      }
    };
  }

  protected void onMixSuccess(WhirlpoolUtxo whirlpoolUtxo, MixSuccess mixSuccess) {
    // override here
  }

  protected void onMixFail(
      WhirlpoolUtxo whirlpoolUtxo, MixFailReason reason, String notifiableError) {
    // override here
  }

  private synchronized void removeMixing(WhirlpoolUtxo whirlpoolUtxo) {
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.remove(key);
    mixingHashs.remove(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
    mixingState.setUtxosMixing(computeUtxosMixing());
  }

  private synchronized void addMixing(Mixing mixingToAdd) {
    WhirlpoolUtxo whirlpoolUtxo = mixingToAdd.getUtxo();
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.put(key, mixingToAdd);
    mixingHashs.add(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
    mixingState.set(
        computeUtxosMixing(),
        getQueue().collect(Collectors.<WhirlpoolUtxo>toList())); // recount nbQueued too
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo, boolean isFirstFetch) {
    // autoQueue BEFORE refreshMixableStatus (which triggers the mix for queued mixable utxos)
    autoQueue(whirlpoolUtxo, isFirstFetch);
    refreshMixableStatus(whirlpoolUtxo);
  }

  private void autoQueue(WhirlpoolUtxo whirlpoolUtxo, boolean isFirstFetch) {
    WhirlpoolUtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfig();
    if (WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getUtxoState().getStatus())
        && utxoConfig.getPoolId() != null) {

      // automix : queue new PREMIX
      boolean isAutoMixPremix =
          autoMix && WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount());
      // queue unfinished POSTMIX utxos
      boolean isAutoRemix =
          (!isFirstFetch || autoMix)
              && WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())
              && !utxoConfig.isDone(mixsTargetMin);

      if (isAutoMixPremix || isAutoRemix) {
        if (log.isDebugEnabled()) {
          log.debug(
              " o AutoMix: new "
                  + whirlpoolUtxo.getAccount()
                  + " utxo detected, adding to mixQueue: "
                  + whirlpoolUtxo);
        }
        try {
          mixQueue(whirlpoolUtxo);
        } catch (Exception e) {
          log.error("", e);
        }
      }
    }
  }

  public void onUtxoUpdated(WhirlpoolUtxo whirlpoolUtxo) {
    refreshMixableStatus(whirlpoolUtxo);
  }

  public void onUtxoRemoved(WhirlpoolUtxo whirlpoolUtxo) {
    boolean wasMixable =
        MixableStatus.MIXABLE.equals(whirlpoolUtxo.getUtxoState().getMixableStatus());
    boolean isMixable = false;
    if (wasMixable != isMixable) {
      onMixableStatusChange(whirlpoolUtxo, wasMixable, isMixable);
    }
  }

  private void onMixableStatusChange(
      WhirlpoolUtxo whirlpoolUtxo, boolean wasMixable, boolean isMixable) {
    if (!wasMixable
        && isMixable
        && WhirlpoolUtxoStatus.MIX_QUEUE.equals(whirlpoolUtxo.getUtxoState().getStatus())) {
      // wakeup on MIXABLE queued utxo
      notifyOrchestrator();
    } else if (wasMixable && !isMixable) {
      // stop mixing non-mixable utxo
      if (log.isDebugEnabled()) {
        log.debug("Stopping NOT MIXABLE mixing: " + whirlpoolUtxo);
      }
      Mixing mixing = getMixing(whirlpoolUtxo.getUtxo());
      if (mixing != null) {
        // stop mixing
        stopWhirlpoolClient(mixing, true, false);
      }
    }
  }

  protected static class Mixing {
    private WhirlpoolUtxo utxo;
    private String poolId;
    private WhirlpoolClient whirlpoolClient;
    private Observable<MixProgress> observable;
    private long since;

    public Mixing(
        WhirlpoolUtxo utxo,
        String poolId,
        WhirlpoolClient whirlpoolClient,
        Observable<MixProgress> observable) {
      this.utxo = utxo;
      this.poolId = poolId;
      this.whirlpoolClient = whirlpoolClient;
      this.observable = observable;
      this.since = System.currentTimeMillis();
    }

    public WhirlpoolUtxo getUtxo() {
      return utxo;
    }

    public String getPoolId() {
      return poolId;
    }

    public WhirlpoolClient getWhirlpoolClient() {
      return whirlpoolClient;
    }

    public Observable<MixProgress> getObservable() {
      return observable;
    }

    public long getSince() {
      return since;
    }

    @Override
    public String toString() {
      return "poolId=" + poolId + ", utxo=[" + utxo + "]";
    }
  }
}
