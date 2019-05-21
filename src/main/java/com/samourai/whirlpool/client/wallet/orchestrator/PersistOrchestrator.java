package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(PersistOrchestrator.class);
  private final WhirlpoolWallet whirlpoolWallet;
  private final int cleanDelay;

  private long lastClean;

  public PersistOrchestrator(int loopDelay, WhirlpoolWallet whirlpoolWallet, int cleanDelay) {
    super(loopDelay);
    this.whirlpoolWallet = whirlpoolWallet;
    this.cleanDelay = cleanDelay;
    this.lastClean = 0;
  }

  @Override
  protected void runOrchestrator() {
    try {
      long now = System.currentTimeMillis();
      if ((now - lastClean) > cleanDelay) {
        // clean
        lastClean = now;
        cleanUtxoConfig();
      }

      // persist
      persist();
    } catch (Exception e) {
      log.error("", e);
    }
  }

  protected void cleanUtxoConfig() throws Exception {
    Collection<WhirlpoolUtxo> knownUtxos =
        whirlpoolWallet.getUtxos(false, WhirlpoolAccount.values());
    whirlpoolWallet.getConfig().getPersistHandler().cleanUtxoConfig(knownUtxos);
  }

  protected void persist() throws Exception {
    whirlpoolWallet.getConfig().getPersistHandler().save();
  }
}
