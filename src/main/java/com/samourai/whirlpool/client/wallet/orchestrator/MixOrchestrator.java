package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoPriorityComparator;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java8.util.function.Function;
import java8.util.function.Predicate;
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
  private long lastMixStarted;

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
    this.lastMixStarted = 0;
  }

  @Override
  protected void runOrchestrator() {
    MixOrchestratorState state = getState();
    String status = "Threads running: " + state.getNbMixing() + "/" + maxClients;
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
        Collection<WhirlpoolUtxo> whirlpoolUtxos = findToMixByPriority(nbIdle);
        if (log.isDebugEnabled()) {
          log.debug("Found " + whirlpoolUtxos.size() + " queued utxos to mix...");
        }
        for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
          // sleep clientDelay
          long elapsedTimeSinceLastMix = System.currentTimeMillis() - lastMixStarted;
          long timeToWait = clientDelay * 1000 - elapsedTimeSinceLastMix;
          if (timeToWait > 0) {
            if (log.isDebugEnabled()) {
              log.debug("Sleeping for clientDelay: " + (timeToWait / 1000) + "s");
            }
            sleepOrchestrator(timeToWait, true);
          }

          // start mix
          lastMixStarted = System.currentTimeMillis();
          mix(whirlpoolUtxo);
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
    int nbIdle = maxClients - mixing.size();
    int nbQueued = toMix.size();
    return new MixOrchestratorState(utxosMixing, maxClients, nbIdle, nbQueued);
  }

  protected List<WhirlpoolUtxo> findToMixByPriority(int nbUtxos) {
    List<WhirlpoolUtxo> results = new ArrayList<WhirlpoolUtxo>();

    // exclude hashs of utxos currently mixing
    Set<String> excludedHashs = new HashSet<String>();
    for (Mixing m : mixing.values()) {
      excludedHashs.add(m.getUtxo().getUtxo().tx_hash);
    }

    while (results.size() < nbUtxos) {
      WhirlpoolUtxo whirlpoolUtxo = findToMixByPriority(excludedHashs);
      if (whirlpoolUtxo == null) {
        // no more utxos eligible yet
        return results;
      }

      // eligible utxo found
      results.add(whirlpoolUtxo);
      excludedHashs.add(whirlpoolUtxo.getUtxo().tx_hash);
    }
    return results;
  }

  protected WhirlpoolUtxo findToMixByPriority(final Set<String> excludedHashs) {
    if (toMix.isEmpty()) {
      return null;
    }
    return StreamSupport.stream(toMix.values())
        // exclude hashs
        .filter(
            new Predicate<WhirlpoolUtxo>() {
              @Override
              public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                return !excludedHashs.contains(whirlpoolUtxo.getUtxo().tx_hash);
              }
            })
        .sorted(new WhirlpoolUtxoPriorityComparator())
        .findFirst()
        .orElse(null);
  }

  public synchronized void mixQueue(WhirlpoolUtxo whirlpoolUtxo) {
    if (whirlpoolUtxo.getPool() == null) {
      log.warn("mixQueue ignored: no pool set for " + whirlpoolUtxo);
      return;
    }
    String key = whirlpoolUtxo.getUtxo().toKey();
    if (!toMix.containsKey(key) && !mixing.containsKey(key)) {
      whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_QUEUE);
      if (log.isDebugEnabled()) {
        log.debug(" + mixQueue: " + whirlpoolUtxo.toString());
      }
      toMix.put(key, whirlpoolUtxo);

      notifyOrchestrator();
    } else {
      log.warn("mixQueue ignored: utxo already queued or mixing: " + whirlpoolUtxo);
    }
  }

  private synchronized void mix(final WhirlpoolUtxo whirlpoolUtxo) {
    final String key = whirlpoolUtxo.getUtxo().toKey();
    WhirlpoolClientListener utxoListener =
        new WhirlpoolClientListener() {
          @Override
          public void success(int nbMixs, MixSuccess mixSuccess) {
            whirlpoolWallet.clearCache(whirlpoolUtxo.getAccount());
            whirlpoolWallet.clearCache(WhirlpoolAccount.POSTMIX);
          }

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
            whirlpoolWallet.clearCache(whirlpoolUtxo.getAccount());
            whirlpoolWallet.clearCache(WhirlpoolAccount.POSTMIX);
            mixing.remove(key);
          }
        };

    // start mix
    WhirlpoolClientListener listener = whirlpoolWallet.mix(whirlpoolUtxo, utxoListener);

    mixing.put(key, new Mixing(whirlpoolUtxo, listener));
    toMix.remove(key);
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    // enqueue unfinished POSTMIX utxos
    if (WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())
        && whirlpoolUtxo.getMixsTarget() < whirlpoolUtxo.getMixsDone()
        && whirlpoolUtxo.getPool() != null) {
      mixQueue(whirlpoolUtxo);
    }
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
