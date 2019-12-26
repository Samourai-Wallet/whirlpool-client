package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
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

public class MixOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(MixOrchestrator.class);
  private static final int LAST_ERROR_DELAY = 60 * 5; // 5min
  private static final int MIXING_SWAP_DELAY = 60 * 2; // 2min

  private WhirlpoolWallet whirlpoolWallet;
  private MixingStateEditable mixingState;
  private Integer maxClients;
  private int maxClientsPerPool;

  private ConcurrentHashMap<String, Mixing> mixing;
  private Set<String> mixingHashs;
  private Map<String, Integer> mixingPerPool;

  public MixOrchestrator(
      int loopDelay,
      WhirlpoolWallet whirlpoolWallet,
      MixingStateEditable mixingState,
      Integer maxClients,
      int maxClientsPerPool,
      int clientDelay) {
    super(loopDelay, 0, clientDelay);
    this.whirlpoolWallet = whirlpoolWallet;
    this.mixingState = mixingState;
    this.maxClients = maxClients;
    this.maxClientsPerPool = maxClientsPerPool;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
    this.mixing = new ConcurrentHashMap<String, Mixing>();
    this.mixingHashs = new HashSet<String>();
    this.mixingPerPool = new HashMap<String, Integer>();
  }

  @Override
  public synchronized void stop() {
    super.stop();
    stopMixingClients();
  }

  private synchronized void stopMixingClients() {
    for (Mixing oneMixing : mixing.values()) {
      mixStop(oneMixing, true);
    }
    mixing.clear();
    mixingHashs.clear();
    mixingPerPool.clear();
    mixingState.setUtxosMixing(computeUtxosMixing());
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

  @Override
  protected void runOrchestrator() {
    try {
      // refresh all mixableStatus
      refreshMixableStatus();

      if (log.isDebugEnabled()) {
        log.debug(mixing.size() + " threads running => checking for queued utxos to mix...");
      }

      // find
      WhirlpoolUtxo whirlpoolUtxo = findForMix();
      if (whirlpoolUtxo == null) {
        // nothing more to mix => exit this loop
        return;
      }
      if (!isStarted()) {
        // wallet stopped in meantime
        return;
      }

      // mix
      whirlpoolWallet.mix(whirlpoolUtxo);
      setLastRun();
    } catch (Exception e) {
      log.error("", e);
    }
  }

  private synchronized WhirlpoolUtxo findForMix() throws Exception {
    // find mixable by priority
    Optional<WhirlpoolUtxo> mixableUtxoOpt = findMixable();
    if (!mixableUtxoOpt.isPresent()) {
      if (log.isDebugEnabled()) {
        log.debug("No additional queued utxo mixable now.");
      }
      return null;
    }

    WhirlpoolUtxo mixableUtxo = mixableUtxoOpt.get();

    if (hasMoreMixingThreadAvailable(mixableUtxo.getUtxoConfig().getPoolId())) {
      // more threads available => start mix
      if (log.isDebugEnabled()) {
        log.debug(
            "(IDLE) Found queued utxo to mix => mix now: "
                + mixableUtxo
                + " ; "
                + mixableUtxo.getUtxoConfig());
      }
      return mixableUtxo;
    } else {
      // all threads running => find a lower priority mixing to swap
      Optional<Mixing> mixingToSwapOpt = findMixingToSwap(mixableUtxo);
      if (!mixingToSwapOpt.isPresent()) {
        // no mixing to swap
        if (log.isDebugEnabled()) {
          log.debug(mixing.size() + " threads running: all threads running.");
        }
        return null;
      }

      Mixing mixingToSwap = mixingToSwapOpt.get();

      // found a mixing to swap
      if (log.isDebugEnabled()) {
        log.debug(
            "(SWAP) Found queued utxo to mix => mix now: "
                + mixableUtxo
                + " ; "
                + mixableUtxo.getUtxoConfig()
                + ", swapping from: "
                + mixingToSwap);
      }
      mixStop(mixingToSwap.getUtxo(), true);
      return mixableUtxo;
    }
  }

  private Optional<Mixing> findMixingToSwap(final WhirlpoolUtxo toMix) {
    final WhirlpoolUtxoPriorityComparator comparator = computeWhirlpoolUtxoPriorityComparator();
    Optional<Mixing> toSwap =
        StreamSupport.stream(mixing.values())
            .filter(
                new Predicate<Mixing>() {
                  @Override
                  public boolean test(Mixing mixing) {
                    // should be waiting for a mix for long enough
                    if (!MixStatus.CONFIRM_INPUT.equals(
                        mixing.getUtxo().getUtxoState().getStatus())) {
                      return false;
                    }
                    long elapsedTime = System.currentTimeMillis() - mixing.getSince();
                    if (elapsedTime < (MIXING_SWAP_DELAY * 1000)) {
                      return false;
                    }

                    // should be lower priority
                    if (comparator.compare(mixing.getUtxo(), toMix) >= 0) {
                      return false;
                    }
                    return true;
                  }
                })
            .findFirst();
    return toSwap;
  }

  private Stream<WhirlpoolUtxo> getQueue() {
    try {
      return StreamSupport.stream(
              whirlpoolWallet.getUtxos(false, WhirlpoolAccount.PREMIX, WhirlpoolAccount.POSTMIX))
          .filter(
              new Predicate<WhirlpoolUtxo>() {
                @Override
                public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                  // queued
                  return WhirlpoolUtxoStatus.MIX_QUEUE.equals(
                      whirlpoolUtxo.getUtxoState().getStatus());
                }
              });
    } catch (Exception e) {
      return StreamSupport.stream(new ArrayList());
    }
  }

  public boolean hasMoreMixableOrUnconfirmed() {
    return getQueueByMixableStatus(false, MixableStatus.MIXABLE, MixableStatus.UNCONFIRMED)
        .findFirst()
        .isPresent();
  }

  public boolean hasMoreMixingThreadAvailable(String poolId) {
    // check maxClients
    if (maxClients != null && mixing.size() >= maxClients) {
      return false;
    }

    // check maxClientsPerPool
    Integer nbMixingInPool = mixingPerPool.get(poolId);
    if (nbMixingInPool != null && nbMixingInPool >= maxClientsPerPool) {
      return false;
    }
    return true;
  }

  private Optional<WhirlpoolUtxo> findMixable() {
    // find highest priority utxo to mix
    return getQueueByMixableStatus(true, MixableStatus.MIXABLE)
        .filter(
            new Predicate<WhirlpoolUtxo>() {
              @Override
              public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                // enforce maxClientsPerPool
                String poolId = whirlpoolUtxo.getUtxoConfig().getPoolId();
                Integer nbMixingInPool = mixingPerPool.get(poolId);
                return nbMixingInPool == null || nbMixingInPool < maxClientsPerPool;
              }
            })
        .findFirst();
  }

  private Map<String, Integer> computeMixingPerPool() {
    Map<String, Integer> mixingPerPool = new HashMap<String, Integer>();
    for (Mixing mixingItem : mixing.values()) {
      String poolId = mixingItem.getPoolId();
      Integer currentCount = mixingPerPool.containsKey(poolId) ? mixingPerPool.get(poolId) : 0;
      mixingPerPool.put(poolId, currentCount + 1);
    }
    return mixingPerPool;
  }

  private Stream<WhirlpoolUtxo> getQueueByMixableStatus(
      final boolean filterErrorDelay, final MixableStatus... filterMixableStatuses) {
    final long lastErrorMax = System.currentTimeMillis() - (LAST_ERROR_DELAY * 1000);

    // find queued
    Stream<WhirlpoolUtxo> toMixByPriority =
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
                })
            .sorted(computeWhirlpoolUtxoPriorityComparator());
    return toMixByPriority;
  }

  public WhirlpoolUtxoPriorityComparator computeWhirlpoolUtxoPriorityComparator() {
    return new WhirlpoolUtxoPriorityComparator(mixingHashs, mixingPerPool);
  }

  private MixableStatus computeMixableStatus(WhirlpoolUtxo whirlpoolUtxo) {

    // check pool
    if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
      return MixableStatus.NO_POOL;
    }

    // check confirmations
    if (whirlpoolUtxo.getUtxo().confirmations < WhirlpoolWallet.MIX_MIN_CONFIRMATIONS) {
      return MixableStatus.UNCONFIRMED;
    }

    if (!WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())) {
      // already mixing?
      final String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
      if (!mixing.contains(key)) {

        // exclude hashs of utxos currently mixing
        if (mixingHashs.contains(whirlpoolUtxo.getUtxo().tx_hash)) {
          return MixableStatus.HASH_MIXING;
        }
      }
    }

    // ok
    return MixableStatus.MIXABLE;
  }

  private MixableStatus refreshMixableStatus(WhirlpoolUtxo whirlpoolUtxo) {
    MixableStatus mixableStatus = computeMixableStatus(whirlpoolUtxo);
    whirlpoolUtxo.getUtxoState().setMixableStatus(mixableStatus);
    return mixableStatus;
  }

  private void refreshMixableStatus() {
    try {
      for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolWallet.getUtxos(false)) {
        refreshMixableStatus(whirlpoolUtxo);
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }

  public void mixQueue(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
    WhirlpoolUtxoStatus utxoStatus = utxoState.getStatus();
    if (!WhirlpoolUtxoStatus.MIX_FAILED.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.READY.equals(utxoStatus)) {
      throw new NotifiableException("cannot add to mix queue: utxoStatus=" + utxoStatus);
    }
    if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
      throw new NotifiableException("cannot add to mix queue: no pool set");
    }
    final String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    if (!mixing.containsKey(key)) {
      // add to queue
      utxoState.setStatus(WhirlpoolUtxoStatus.MIX_QUEUE, false);
      if (log.isDebugEnabled()) {
        log.debug(" + mixQueue: " + whirlpoolUtxo.toString());
      }
      mixingState.incrementNbQueued();
      notifyOrchestrator();
    } else {
      log.warn("mixQueue ignored: utxo already queued or mixing");
    }
  }

  public synchronized void mixStop(WhirlpoolUtxo whirlpoolUtxo, boolean cancel) {
    WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
    boolean wasQueued = WhirlpoolUtxoStatus.MIX_QUEUE.equals(utxoState.getStatus());

    // set status (this eventually removes it from queue)
    WhirlpoolUtxoStatus utxoStatus = cancel ? WhirlpoolUtxoStatus.READY : WhirlpoolUtxoStatus.STOP;
    utxoState.setStatus(utxoStatus, false);

    final String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    Mixing myMixing = mixing.get(key);
    if (myMixing != null) {
      // stop mixing
      mixStop(myMixing, cancel);
    } else if (wasQueued) {
      // recount QUEUE if it was queued
      mixingState.setNbQueued((int) getQueue().count());
    }
  }

  protected void mixStop(final Mixing mixing, final boolean cancel) {
    if (log.isDebugEnabled()) {
      if (cancel) {
        log.debug("Canceling mixing client: " + mixing);
      } else {
        log.debug("Stopping mixing client: " + mixing);
      }
    }

    // stop in new thread for faster response
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                mixing.getWhirlpoolClient().stop(cancel);
              }
            },
            "stop-whirlpoolClient")
        .start();
  }

  public Observable<MixProgress> doMix(
      final WhirlpoolUtxo whirlpoolUtxo, MixParams mixParams, WhirlpoolClient mixClient) {
    MixProgress mixProgress = new MixProgress(MixStep.CONNECTING);
    whirlpoolUtxo.getUtxoState().setStatus(WhirlpoolUtxoStatus.MIX_STARTED, true, mixProgress);
    if (log.isDebugEnabled()) {
      log.info(
          " • Connecting client to pool: "
              + whirlpoolUtxo.getUtxoConfig().getPoolId()
              + ", utxo="
              + whirlpoolUtxo
              + " ; "
              + whirlpoolUtxo.getUtxoConfig());
    } else {
      log.info(" • Connecting client to pool: " + whirlpoolUtxo.getUtxoConfig().getPoolId());
    }

    // start mixing (whirlpoolClient will start a new thread)
    Subject<MixProgress> mixStateObservable = BehaviorSubject.create();
    WhirlpoolClientListener listener = computeListener(whirlpoolUtxo, mixStateObservable);
    mixClient.whirlpool(mixParams, listener);

    String poolId = whirlpoolUtxo.getUtxoConfig().getPoolId();
    Mixing mixing = new Mixing(whirlpoolUtxo, poolId, mixClient);
    addMixing(mixing);

    return mixStateObservable;
  }

  private WhirlpoolClientListener computeListener(
      final WhirlpoolUtxo whirlpoolUtxo, final Subject<MixProgress> mixStateObservable) {
    return new LoggingWhirlpoolClientListener() {
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
        whirlpoolWallet.onMixSuccess(whirlpoolUtxo, mixSuccess);

        // notify mixProgress
        mixStateObservable.onNext(mixProgress);
        mixStateObservable.onComplete();

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
        whirlpoolWallet.onMixFail(whirlpoolUtxo, reason, notifiableError);

        // notify mixProgress
        mixStateObservable.onNext(mixProgress);
        mixStateObservable.onComplete();

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
        mixStateObservable.onNext(mixProgress);
      }
    };
  }

  private void removeMixing(WhirlpoolUtxo whirlpoolUtxo) {
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.remove(key);
    mixingHashs.remove(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
    mixingState.setUtxosMixing(computeUtxosMixing());
  }

  private void addMixing(Mixing mixingToAdd) {
    if (log.isDebugEnabled()) {
      log.debug("addMixing: " + mixingToAdd.getUtxo());
    }
    WhirlpoolUtxo whirlpoolUtxo = mixingToAdd.getUtxo();
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.put(key, mixingToAdd);
    mixingHashs.add(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
    mixingState.set(computeUtxosMixing(), (int) getQueue().count()); // recount nbQueued too
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo, boolean isFirstFetch) {
    // set mixableStatus
    refreshMixableStatus(whirlpoolUtxo);

    WhirlpoolUtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfig();

    if (log.isDebugEnabled()) {
      log.debug("onUtxoDetected: " + whirlpoolUtxo + " ; " + utxoConfig);
    }

    boolean isAutoMix = whirlpoolWallet.getConfig().isAutoMix();
    if (!isFirstFetch || isAutoMix) {
      // enqueue unfinished POSTMIX utxos
      int mixsTargetOrDefault =
          utxoConfig.getMixsTargetOrDefault(whirlpoolWallet.getConfig().getMixsTarget());
      if (WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())
          && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getUtxoState().getStatus())
          && (utxoConfig.getMixsDone() < mixsTargetOrDefault
              || mixsTargetOrDefault == WhirlpoolUtxoConfig.MIXS_TARGET_UNLIMITED)
          && utxoConfig.getPoolId() != null) {

        if (log.isDebugEnabled()) {
          log.debug(
              " o Mix: new POSTMIX utxo detected, adding to mixQueue: "
                  + whirlpoolUtxo
                  + " ; "
                  + utxoConfig);
        }
        try {
          whirlpoolWallet.mixQueue(whirlpoolUtxo);
        } catch (Exception e) {
          log.error("onUtxoDetected failed", e);
        }
      }
    }
  }

  public void onUtxoConfirmed(WhirlpoolUtxo whirlpoolUtxo) {
    // refresh mixableStatus for this utxos
    refreshMixableStatus(whirlpoolUtxo);

    // wakeup on confirmed PREMIX/POSTMIX
    if (WhirlpoolUtxoStatus.MIX_QUEUE.equals(whirlpoolUtxo.getUtxoState().getStatus())
        && whirlpoolUtxo.getUtxo().confirmations >= WhirlpoolWallet.MIX_MIN_CONFIRMATIONS) {
      log.info(" o Mix: new CONFIRMED utxo detected, checking for mix: " + whirlpoolUtxo);
      notifyOrchestrator();
    }
  }

  private static class Mixing {
    private WhirlpoolUtxo utxo;
    private String poolId;
    private WhirlpoolClient whirlpoolClient;
    private long since;

    public Mixing(WhirlpoolUtxo utxo, String poolId, WhirlpoolClient whirlpoolClient) {
      this.utxo = utxo;
      this.poolId = poolId;
      this.whirlpoolClient = whirlpoolClient;
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

    public long getSince() {
      return since;
    }

    @Override
    public String toString() {
      return "poolId=" + poolId + ", utxo=[" + utxo + "]";
    }
  }
}
