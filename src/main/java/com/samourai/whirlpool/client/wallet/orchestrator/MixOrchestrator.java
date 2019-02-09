package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(MixOrchestrator.class);
  private static final int LOOP_DELAY = 120000;
  private WhirlpoolWallet whirlpoolWallet;
  private int maxClients;
  private int clientDelay;

  private Map<String, WhirlpoolUtxo> toMix;
  private Map<String, Mixing> mixing;

  public MixOrchestrator(WhirlpoolWallet whirlpoolWallet, int maxClients, int clientDelay) {
    super(LOOP_DELAY);
    this.whirlpoolWallet = whirlpoolWallet;
    this.maxClients = maxClients;
    this.clientDelay = clientDelay;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
    this.toMix = new HashMap<String, WhirlpoolUtxo>();
    this.mixing = new HashMap<String, Mixing>();
  }

  @Override
  protected synchronized void runOrchestrator() {
    MixOrchestratorState state = getState();
    String status =
        "Mixing threads: "
            + state.getNbMixing()
            + "/"
            + maxClients
            + " ("
            + state.getNbQueued()
            + " queued, "
            + state.getNbIdle()
            + " idle)";
    int nbIdle = state.getNbIdle();
    if (nbIdle > 0) {
      // more clients available
      if (log.isDebugEnabled()) {
        log.debug(
            status
                + ". More threads available, checking for queued utxos to mix... ("
                + toMix.size()
                + " queued)");
        ClientUtils.logWhirlpoolUtxos(toMix.values());
      }
      try {
        // start mixing up to nbIdle utxos
        for (int i = 0; i < nbIdle && !toMix.isEmpty(); i++) {
          WhirlpoolUtxo whirlpoolUtxo = findToMixByPriority();
          if (whirlpoolUtxo != null) {
            // sleep clientDelay
            if (i > 0 && clientDelay > 0) {
              try {
                Thread.sleep(clientDelay * 1000);
              } catch (InterruptedException e) {
              }
            }

            // start mixing
            mix(whirlpoolUtxo);
          }
        }
      } catch (Exception e) {
        log.error("", e);
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug(status + ". All threads running.");
        ClientUtils.logWhirlpoolUtxos(toMix.values());
      }
      // no client available
    }
  }

  protected MixOrchestratorState getState() {
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
    int nbIdle = maxClients - mixing.size();
    int nbQueued = toMix.size();
    return new MixOrchestratorState(utxosMixing, maxClients, nbIdle, nbQueued);
  }

  protected synchronized WhirlpoolUtxo findToMixByPriority() {
    if (toMix.isEmpty()) {
      return null;
    }
    return toMix.values().iterator().next(); // TODO priority
  }

  public synchronized void addToMix(WhirlpoolUtxo whirlpoolUtxo) {
    if (whirlpoolUtxo.getPool() == null) {
      log.warn("addToMix ignored: no pool set for " + whirlpoolUtxo);
      return;
    }
    String key = whirlpoolUtxo.getUtxo().toKey();
    if (!toMix.containsKey(key) && !mixing.containsKey(key)) {
      whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_QUEUE);
      if (log.isDebugEnabled()) {
        log.debug(" + Queued to mix: " + whirlpoolUtxo.toString());
      }
      toMix.put(key, whirlpoolUtxo);
      notifyOrchestrator();
    } else {
      log.warn("addToMix ignored: utxo already queued or mixing: " + whirlpoolUtxo);
    }
  }

  private synchronized void mix(WhirlpoolUtxo whirlpoolUtxo) {
    final String key = whirlpoolUtxo.getUtxo().toKey();
    WhirlpoolClientListener utxoListener =
        new WhirlpoolClientListener() {
          @Override
          public void success(int nbMixs, MixSuccess mixSuccess) {}

          @Override
          public void fail(int currentMix, int nbMixs) {
            mixing.remove(key);
          }

          @Override
          public void progress(
              int currentMix,
              int nbMixs,
              MixStep step,
              String stepInfo,
              int stepNumber,
              int nbSteps) {}

          @Override
          public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
            mixing.remove(key);
          }
        };

    // start mix
    WhirlpoolClientListener listener = whirlpoolWallet.mix(whirlpoolUtxo, utxoListener);

    mixing.put(key, new Mixing(whirlpoolUtxo, listener));
    toMix.remove(key);
  }

  private static class Mixing {
    private WhirlpoolUtxo utxo;
    private WhirlpoolClientListener listener;

    public Mixing(WhirlpoolUtxo utxo, WhirlpoolClientListener listener) {
      this.utxo = utxo;
      this.listener = listener;
    }

    public WhirlpoolUtxo getUtxo() {
      return utxo;
    }

    public WhirlpoolClientListener getListener() {
      return listener;
    }
  }
}
