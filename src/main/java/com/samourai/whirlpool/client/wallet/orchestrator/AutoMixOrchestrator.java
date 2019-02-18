package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoMixOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(AutoMixOrchestrator.class);
  private WhirlpoolWallet whirlpoolWallet;

  public AutoMixOrchestrator(WhirlpoolWallet whirlpoolWallet, int loopDelay) {
    super(loopDelay, "AutoMixOrchestrator");
    this.whirlpoolWallet = whirlpoolWallet;
  }

  @Override
  protected void runOrchestrator() {
    try {
      resumePremix();
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected void resumePremix() throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("Checking for PREMIX utxos ready to mix...");
    }
    // rescan premix
    whirlpoolWallet.getUtxosPremix(true);
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    try {
      if (WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())
          && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getStatus())) {

        // assign pool if not already assigned
        if (whirlpoolUtxo.getPool() == null) {
          long utxoValue = whirlpoolUtxo.getUtxo().value;
          Collection<Pool> pools = whirlpoolWallet.findPoolsByPriorityForPremix(utxoValue);
          if (pools.isEmpty()) {
            log.warn("No pool for this utxo balance: " + whirlpoolUtxo.toString());
            whirlpoolUtxo.setError("No pool for this utxo balance");
            return;
          }

          // assign pool from biggest denomination possible
          whirlpoolUtxo.setPool(pools.iterator().next());
        }

        log.info(" o AutoMix: new PREMIX utxo detected, adding to mixQueue: " + whirlpoolUtxo);
        whirlpoolWallet.mixQueue(whirlpoolUtxo);
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
