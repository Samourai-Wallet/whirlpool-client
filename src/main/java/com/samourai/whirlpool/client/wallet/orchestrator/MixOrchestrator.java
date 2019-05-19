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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
  private WhirlpoolWallet whirlpoolWallet;
  private int maxClients;
  private int clientDelay;

  private ConcurrentHashMap<String, Mixing> mixing;
  private Set<String> mixingHashs;

  public MixOrchestrator(
      int loopDelay, WhirlpoolWallet whirlpoolWallet, int maxClients, int clientDelay) {
    super(loopDelay);
    this.whirlpoolWallet = whirlpoolWallet;
    this.maxClients = maxClients;
    this.clientDelay = clientDelay;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
    this.mixing = new ConcurrentHashMap<String, Mixing>();
    this.mixingHashs = new HashSet<String>();
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
  }

  @Override
  protected void runOrchestrator() {
    try {
      // check idles
      while (computeNbIdle() > 0) {

        // sleep clientDelay
        boolean waited = waitForLastRunDelay(clientDelay, "Sleeping for clientDelay");
        if (waited) {
          // re-check for idle on wakeup
          if (computeNbIdle() == 0) {
            return;
          }
        }

        // idles detected
        if (log.isDebugEnabled()) {
          log.debug(
              getState().getNbMixing()
                  + "/"
                  + maxClients
                  + " threads running => checking for queued utxos to mix...");
        }

        // find & mix
        boolean startedNewMix = findAndMix();

        // refresh all mixableStatus (either if we started new mix or not, to clean state before
        // sleeping)
        refreshMixableStatus();

        if (!startedNewMix) {
          // nothing more to mix => exit this loop
          return;
        }
      }

      // all threads running
      if (log.isDebugEnabled()) {
        log.debug(
            getState().getNbMixing() + "/" + maxClients + " threads running: all threads running.");
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }

  private synchronized boolean findAndMix() throws Exception {
    // start mixing up to nbIdle utxos
    WhirlpoolUtxo whirlpoolUtxo = findMixable();
    if (whirlpoolUtxo == null) {
      if (log.isDebugEnabled()) {
        log.debug("No additional queued utxo mixable now.");
      }
      return false;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Found queued utxo to mix => mix now: "
              + whirlpoolUtxo
              + " ; "
              + whirlpoolUtxo.getUtxoConfig());
    }

    // start mix
    setLastRun();
    mix(whirlpoolUtxo);
    return true;
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
    int nbIdle = maxClients - mixing.size();
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
    return getQueueByMixableStatus(MixableStatus.MIXABLE, MixableStatus.UNCONFIRMED) != null;
  }

  private WhirlpoolUtxo findMixable() {
    return getQueueByMixableStatus(MixableStatus.MIXABLE);
  }

  private WhirlpoolUtxo getQueueByMixableStatus(MixableStatus... filterMixableStatuses) {
    // find queued
    Collection<WhirlpoolUtxo> toMixByPriority =
        getQueue()
            .sorted(new WhirlpoolUtxoPriorityComparator())
            .collect(Collectors.<WhirlpoolUtxo>toList());

    for (WhirlpoolUtxo whirlpoolUtxo : toMixByPriority) {
      // recompute mixableStatus
      MixableStatus mixableStatus = refreshMixableStatus(whirlpoolUtxo);

      // check mixableStatus
      if (ArrayUtils.contains(filterMixableStatuses, mixableStatus)) {
        return whirlpoolUtxo;
      }
    }
    return null;
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
      whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_QUEUE);
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
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.READY);
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
    Mixing mixing = new Mixing(whirlpoolUtxo, utxoListener, whirlpoolClient);
    addMixing(mixing);
  }

  private void removeMixing(WhirlpoolUtxo whirlpoolUtxo) {
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.remove(key);
    mixingHashs.remove(whirlpoolUtxo.getUtxo().tx_hash);
  }

  private void addMixing(Mixing mixingToAdd) {
    if (log.isDebugEnabled()) {
      log.debug("addMixing: " + mixingToAdd.getUtxo());
    }
    WhirlpoolUtxo whirlpoolUtxo = mixingToAdd.getUtxo();
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.put(key, mixingToAdd);
    mixingHashs.add(whirlpoolUtxo.getUtxo().tx_hash);
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    // set mixableStatus
    refreshMixableStatus(whirlpoolUtxo);

    WhirlpoolUtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfig();

    if (log.isDebugEnabled()) {
      log.debug("onUtxoDetected: " + whirlpoolUtxo + " ; " + utxoConfig);
    }

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
    private WhirlpoolClientListener listener;
    private WhirlpoolClient whirlpoolClient;

    public Mixing(
        WhirlpoolUtxo utxo, WhirlpoolClientListener listener, WhirlpoolClient whirlpoolClient) {
      this.utxo = utxo;
      this.listener = listener;
      this.whirlpoolClient = whirlpoolClient;
    }

    public WhirlpoolUtxo getUtxo() {
      return utxo;
    }

    public WhirlpoolClientListener getListener() {
      return listener;
    }

    public WhirlpoolClient getWhirlpoolClient() {
      return whirlpoolClient;
    }
  }
}
