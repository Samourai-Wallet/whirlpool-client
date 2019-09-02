package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.MixableStatus;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoPriorityComparator;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
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
  private int maxClients;
  private int maxClientsPerPool;
  private int clientDelay;

  private ConcurrentHashMap<String, Mixing> mixing;
  private Set<String> mixingHashs;
  private Map<String, Integer> mixingPerPool;

  public MixOrchestrator(
      int loopDelay,
      WhirlpoolWallet whirlpoolWallet,
      int maxClients,
      int maxClientsPerPool,
      int clientDelay) {
    super(loopDelay);
    this.whirlpoolWallet = whirlpoolWallet;
    this.maxClients = maxClients;
    this.maxClientsPerPool = maxClientsPerPool;
    this.clientDelay = clientDelay;
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

  public synchronized void stopMixingClients() {
    for (Mixing oneMixing : mixing.values()) {
      if (log.isDebugEnabled()) {
        log.debug("Stopping mixing client: " + oneMixing.getUtxo());
      }
      oneMixing.getWhirlpoolClient().exit();
    }
    mixing.clear();
    mixingHashs.clear();
    mixingPerPool.clear();
  }

  @Override
  protected void runOrchestrator() {
    try {
      // check idles
      while (true) {

        // sleep clientDelay
        waitForLastRunDelay(clientDelay, "Sleeping for clientDelay");

        // refresh all mixableStatus
        refreshMixableStatus();

        if (log.isDebugEnabled()) {
          log.debug(
              getState().getNbMixing()
                  + "/"
                  + maxClients
                  + " threads running => checking for queued utxos to mix...");
        }

        // find & mix
        boolean startedNewMix = findAndMix();
        if (!startedNewMix) {
          // nothing more to mix => exit this loop
          return;
        }
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }

  private synchronized boolean findAndMix() throws Exception {
    // find mixable by priority
    Optional<WhirlpoolUtxo> mixableUtxoOpt = findMixable();
    if (!mixableUtxoOpt.isPresent()) {
      if (log.isDebugEnabled()) {
        log.debug("No additional queued utxo mixable now.");
      }
      return false;
    }

    WhirlpoolUtxo mixableUtxo = mixableUtxoOpt.get();

    if (computeNbIdle() == 0) {
      // all threads running => find a lower priority mixing to swap
      Optional<Mixing> mixingToSwapOpt = findMixingToSwap(mixableUtxo);
      if (!mixingToSwapOpt.isPresent()) {
        // no mixing to swap
        if (log.isDebugEnabled()) {
          log.debug(
              getState().getNbMixing()
                  + "/"
                  + maxClients
                  + " threads running: all threads running.");
        }
        return false;
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
      mixStop(mixingToSwap.getUtxo());
      mix(mixableUtxo);
      return true;
    }

    // IDLE slot available => start mix
    if (log.isDebugEnabled()) {
      log.debug(
          "(IDLE) Found queued utxo to mix => mix now: "
              + mixableUtxo
              + " ; "
              + mixableUtxo.getUtxoConfig());
    }
    mix(mixableUtxo);
    return true;
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
                    if (!MixStatus.CONFIRM_INPUT.equals(mixing.getUtxo().getStatus())) {
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

  public MixOrchestratorState getState() {
    List<WhirlpoolUtxo> utxosMixing =
        StreamSupport.stream(mixing.values())
            .map(
                new Function<Mixing, WhirlpoolUtxo>() {
                  @Override
                  public WhirlpoolUtxo apply(Mixing m) {
                    return m.getUtxo();
                  }
                })
            .collect(Collectors.<WhirlpoolUtxo>toList());
    int nbIdle = computeNbIdle();
    int nbQueued = (int) getQueue().count();
    return new MixOrchestratorState(utxosMixing, maxClients, nbIdle, nbQueued);
  }

  private int computeNbIdle() {
    int nbIdle = Math.max(0, maxClients - mixing.size());
    return nbIdle;
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
                  return WhirlpoolUtxoStatus.MIX_QUEUE.equals(whirlpoolUtxo.getStatus());
                }
              });
    } catch (Exception e) {
      return StreamSupport.stream(new ArrayList());
    }
  }

  public boolean hasMoreMixableOrUnconfirmed() {
    return getQueueByMixableStatus(false, MixableStatus.MIXABLE, MixableStatus.UNCONFIRMED)
            .findFirst()
        != null;
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
                    // don't retry before errorDelay
                    boolean accepted =
                        (!filterErrorDelay
                            || whirlpoolUtxo.getLastError() == null
                            || whirlpoolUtxo.getLastError() < lastErrorMax);
                    if (!accepted) {
                      return false;
                    }

                    // filter by mixableStatus
                    return ArrayUtils.contains(
                        filterMixableStatuses, whirlpoolUtxo.getMixableStatus());
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
    whirlpoolUtxo.setMixableStatus(mixableStatus);
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
    WhirlpoolUtxoStatus utxoStatus = whirlpoolUtxo.getStatus();
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
      whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_QUEUE, false);
      if (log.isDebugEnabled()) {
        log.debug(" + mixQueue: " + whirlpoolUtxo.toString());
      }
      notifyOrchestrator();
    } else {
      log.warn("mixQueue ignored: utxo already queued or mixing");
    }
  }

  public synchronized void mixStop(WhirlpoolUtxo whirlpoolUtxo) {
    WhirlpoolUtxoStatus utxoStatus = whirlpoolUtxo.getStatus();
    if (!WhirlpoolUtxoStatus.MIX_QUEUE.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.MIX_FAILED.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.MIX_STARTED.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.MIX_SUCCESS.equals(utxoStatus)) {
      log.warn("mixStop ignored: utxoStatus=" + utxoStatus);
      return;
    }

    // stop mixing
    final String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    Mixing myMixing = mixing.get(key);
    if (myMixing != null) {
      // already mixing
      myMixing.getWhirlpoolClient().exit();
    }
    removeMixing(whirlpoolUtxo);
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.READY, false);
  }

  private synchronized void mix(final WhirlpoolUtxo whirlpoolUtxo) throws Exception {
    WhirlpoolClientListener utxoListener =
        new WhirlpoolClientListener() {
          @Override
          public void success(MixSuccess mixSuccess) {
            whirlpoolWallet.clearCache(whirlpoolUtxo.getAccount());
            whirlpoolWallet.clearCache(WhirlpoolAccount.POSTMIX);
            removeMixing(whirlpoolUtxo);

            // idle => notify orchestrator
            notifyOrchestrator();
          }

          @Override
          public void fail(MixFailReason reason, String notifiableError) {
            removeMixing(whirlpoolUtxo);

            // idle => notify orchestrator
            notifyOrchestrator();
          }

          @Override
          public void progress(MixStep step) {}
        };

    // start mix
    WhirlpoolClient whirlpoolClient = whirlpoolWallet.mix(whirlpoolUtxo, utxoListener);
    String poolId = whirlpoolUtxo.getUtxoConfig().getPoolId();
    Mixing mixing = new Mixing(whirlpoolUtxo, poolId, utxoListener, whirlpoolClient);
    addMixing(mixing);
    setLastRun();
  }

  private void removeMixing(WhirlpoolUtxo whirlpoolUtxo) {
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.remove(key);
    mixingHashs.remove(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
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
      if (WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())
          && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getStatus())
          && (utxoConfig.getMixsDone() < utxoConfig.getMixsTarget()
              || utxoConfig.getMixsTarget() == WhirlpoolUtxoConfig.MIXS_TARGET_UNLIMITED)
          && utxoConfig.getPoolId() != null) {

        log.info(
            " o Mix: new POSTMIX utxo detected, adding to mixQueue: "
                + whirlpoolUtxo
                + " ; "
                + utxoConfig);
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
    if (WhirlpoolUtxoStatus.MIX_QUEUE.equals(whirlpoolUtxo.getStatus())
        && whirlpoolUtxo.getUtxo().confirmations >= WhirlpoolWallet.MIX_MIN_CONFIRMATIONS) {
      log.info(" o Mix: new CONFIRMED utxo detected, checking for mix: " + whirlpoolUtxo);
      notifyOrchestrator();
    }
  }

  private static class Mixing {
    private WhirlpoolUtxo utxo;
    private String poolId;
    private WhirlpoolClientListener listener;
    private WhirlpoolClient whirlpoolClient;
    private long since;

    public Mixing(
        WhirlpoolUtxo utxo,
        String poolId,
        WhirlpoolClientListener listener,
        WhirlpoolClient whirlpoolClient) {
      this.utxo = utxo;
      this.poolId = poolId;
      this.listener = listener;
      this.whirlpoolClient = whirlpoolClient;
      this.since = System.currentTimeMillis();
    }

    public WhirlpoolUtxo getUtxo() {
      return utxo;
    }

    public String getPoolId() {
      return poolId;
    }

    public WhirlpoolClientListener getListener() {
      return listener;
    }

    public WhirlpoolClient getWhirlpoolClient() {
      return whirlpoolClient;
    }

    public long getSince() {
      return since;
    }
  }
}
