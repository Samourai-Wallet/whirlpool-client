package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTx0Orchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(AutoTx0Orchestrator.class);
  private static final int LOOP_DELAY = 120000;

  private WhirlpoolWallet whirlpoolWallet;
  private MixOrchestrator mixOrchestrator;
  private int tx0Delay;

  public AutoTx0Orchestrator(
      WhirlpoolWallet whirlpoolWallet, MixOrchestrator mixOrchestrator, int tx0Delay) {
    super(LOOP_DELAY, "AutoTx0Orchestrator");
    this.whirlpoolWallet = whirlpoolWallet;
    this.mixOrchestrator = mixOrchestrator;
    this.tx0Delay = tx0Delay;
  }

  @Override
  protected void runOrchestrator() {
    if (mixOrchestrator.isDontDisturb()) {
      // no need for more tx0 when mixOrchestrator is sleeping for clientDelay
      if (log.isDebugEnabled()) {
        log.debug("AutoTx0: skipping (MixOrchestrator is sleeping for clientDelay)");
      }
      return;
    }

    // do we have idle threads?
    try {
      int missingMustMixUtxos = whirlpoolWallet.getState().getMixState().getNbIdle();
      if (missingMustMixUtxos > 0) {
        // not enough mustMixUtxos => Tx0
        for (int i = 0; i < missingMustMixUtxos; i++) {
          waitForLastRunDelay(tx0Delay, "Sleeping for tx0Delay");

          // try tx0 with automatic selection of best available utxo
          try {
            whirlpoolWallet.tx0(); // throws UnconfirmedUtxoException, EmptyWalletException
            log.info(" • Tx0 (" + (i + 1) + "/" + missingMustMixUtxos + "): SUCCESS");
          } catch (UnconfirmedUtxoException e) {
            String message =
                " • Tx0 ("
                    + (i + 1)
                    + "/"
                    + missingMustMixUtxos
                    + "): waiting for deposit confirmation";
            if (log.isDebugEnabled()) {
              UnspentOutput utxo = e.getUtxo();
              log.debug(message + ": " + utxo.toString());
            } else {
              log.info(message);
            }

            // no tx0 can be made now, wait for spendFrom to confirm...
            break;
          }
        }
      }
    } catch (EmptyWalletException e) {
      whirlpoolWallet.onEmptyWalletException(e);
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
