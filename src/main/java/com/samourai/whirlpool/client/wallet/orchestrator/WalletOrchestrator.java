package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.wallet.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WalletOrchestrator extends AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(WalletOrchestrator.class);
  private static final int LOOP_DELAY = 120000;
  private WhirlpoolWallet whirlpoolWallet;
  private MixOrchestrator mixOrchestrator;

  public WalletOrchestrator(WhirlpoolWallet whirlpoolWallet, int maxClients, int clientDelay) {
    super(LOOP_DELAY);
    this.whirlpoolWallet = whirlpoolWallet;
    this.mixOrchestrator = new MixOrchestrator(whirlpoolWallet, maxClients, clientDelay);
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
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
    // find utxos from premix
    for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolWallet.getUtxosPremix()) {
      resume(whirlpoolUtxo);
    }
  }

  protected void resume(WhirlpoolUtxo whirlpoolUtxoPremixOrPostmix) throws Exception {
    if (WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxoPremixOrPostmix.getStatus())) {

      // assign pool if not already assigned
      if (whirlpoolUtxoPremixOrPostmix.getPool() == null) {
        Collection<Pool> pools =
            whirlpoolWallet.getPools().findForPremix(whirlpoolUtxoPremixOrPostmix);
        if (pools.isEmpty()) {
          log.error(
              "Cannot resume mixing for utxo: no pool for this denomination: "
                  + whirlpoolUtxoPremixOrPostmix.toString());
          whirlpoolUtxoPremixOrPostmix.setError("No pool for this denomination");
          return;
        }

        // assign pool from biggest denomination possible
        whirlpoolUtxoPremixOrPostmix.setPool(pools.iterator().next());
      }

      addToMix(whirlpoolUtxoPremixOrPostmix);
    }
  }

  @Override
  public synchronized void start() {
    super.start();
    this.mixOrchestrator.start();
  }

  @Override
  public synchronized void stop() {
    super.stop();
    this.mixOrchestrator.stop();
  }

  public synchronized void addToMix(WhirlpoolUtxo whirlpoolUtxo) {
    this.mixOrchestrator.addToMix(whirlpoolUtxo);
  }

  public void onUtxoAdded(WhirlpoolUtxo whirlpoolUtxo) {
    if (WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())) { // TODO resume POSTMIX
      try {
        resume(whirlpoolUtxo);
      } catch (Exception e) {
        log.error("", e);
      }
    }
  }

  public WhirlpoolWalletState getState() {
    MixOrchestratorState mixState = mixOrchestrator.getState();
    return new WhirlpoolWalletState(isStarted(), mixState);
  }
}
