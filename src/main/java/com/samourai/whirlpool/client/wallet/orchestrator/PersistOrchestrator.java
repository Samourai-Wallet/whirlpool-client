package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(PersistOrchestrator.class);
  private WhirlpoolWallet whirlpoolWallet;

  public PersistOrchestrator(int loopDelay, WhirlpoolWallet whirlpoolWallet) {
    super(loopDelay);
    this.whirlpoolWallet = whirlpoolWallet;
  }

  @Override
  protected void runOrchestrator() {
    try {
      persist();
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected void persist() throws Exception {
    whirlpoolWallet.getWalletPersistHandler().save();

    // rescan premix
    whirlpoolWallet.getUtxosPremix(false);
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    try {
      if (whirlpoolUtxo.getUtxoConfig().getPoolId() != null
          && WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())
          && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getStatus())) {

        log.info(" o AutoMix: new PREMIX utxo detected, adding to mixQueue: " + whirlpoolUtxo);
        whirlpoolWallet.mixQueue(whirlpoolUtxo);
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
