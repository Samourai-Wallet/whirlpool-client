package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoMixOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(AutoMixOrchestrator.class);
  private WhirlpoolWallet whirlpoolWallet;

  public AutoMixOrchestrator(int loopDelay, WhirlpoolWallet whirlpoolWallet) {
    super(loopDelay);
    this.whirlpoolWallet = whirlpoolWallet;
  }

  @Override
  protected void runOrchestrator() {
    try {
      resumePremixPostmix();
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected void resumePremixPostmix() throws Exception {
    if (log.isDebugEnabled()) {
      log.debug("Checking for utxos ready to mix...");
    }
    // rescan premix
    whirlpoolWallet.getUtxosPremix(false);

    // rescan postmix
    whirlpoolWallet.getUtxosPostmix(false);
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo, boolean isFirstFetch) {
    try {
      if (whirlpoolUtxo.getUtxoConfig().getPoolId() != null
          && WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())
          && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getUtxoState().getStatus())) {

        if (log.isDebugEnabled()) {
          log.debug(" o AutoMix: new PREMIX utxo detected, adding to mixQueue: " + whirlpoolUtxo);
        }
        whirlpoolWallet.mixQueue(whirlpoolUtxo);
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
