package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTx0Orchestrator extends AbstractOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(AutoTx0Orchestrator.class);
  private static final int LOOP_DELAY = 120000;

  private WhirlpoolWallet whirlpoolWallet;
  private int tx0Delay;

  public AutoTx0Orchestrator(WhirlpoolWallet whirlpoolWallet, int tx0Delay) {
    super(LOOP_DELAY, "AutoTx0Orchestrator");
    this.whirlpoolWallet = whirlpoolWallet;
    this.tx0Delay = tx0Delay;
  }

  @Override
  protected void runOrchestrator() {
    try {
      while (true) {
        waitForLastRunDelay(tx0Delay, "Sleeping for tx0Delay");

        // try tx0 with automatic selection of best available utxo
        try {
          if (log.isDebugEnabled()) {
            log.debug("AutoTx0: attempting new Tx0...");
          }
          whirlpoolWallet.tx0(); // throws UnconfirmedUtxoException, EmptyWalletException
          log.info(" • AutoTx0: SUCCESS");

          // continue for next Tx0...

        } catch (UnconfirmedUtxoException e) {
          String message = " • AutoTx0: waiting for deposit confirmation";
          if (log.isDebugEnabled()) {
            UnspentOutput utxo = e.getUtxo();
            log.debug(message + ": " + utxo.toString());
          } else {
            log.info(message);
          }

          // no tx0 can be made now, wait for spendFrom to confirm...
          break;
        } catch (EmptyWalletException e) {
          if (log.isDebugEnabled()) {
            log.debug("AutoTx0: no Tx0 candidate yet.");
          }

          // no tx0 can be made now, check back later...
          break;
        }
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }

  public void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    try {
      if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())
          && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getStatus())) {

        log.info(" o AutoTx0: new DEPOSIT utxo detected: " + whirlpoolUtxo);
        notifyOrchestrator();
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }
}
