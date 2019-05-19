package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTx0Orchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(AutoTx0Orchestrator.class);
  // start delay to make sure UTXOS are refreshed when starting
  private static final int START_DELAY = 15000;

  private WhirlpoolWallet whirlpoolWallet;
  private int tx0Delay;

  public AutoTx0Orchestrator(int loopDelay, WhirlpoolWallet whirlpoolWallet, int tx0Delay) {
    super(loopDelay, START_DELAY);
    this.whirlpoolWallet = whirlpoolWallet;
    this.tx0Delay = tx0Delay;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
  }

  @Override
  protected void runOrchestrator() {
    try {
      while (true) {
        waitForLastRunDelay(tx0Delay, "Sleeping for tx0Delay");

        // try tx0 with automatic selection of best available utxo
        try {
          if (log.isDebugEnabled()) {
            log.debug("AutoTx0: looking for Tx0...");
          }
          whirlpoolWallet.autoTx0(); // throws UnconfirmedUtxoException, EmptyWalletException
          setLastRun();
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
          // make sure that mixOrchestrator has no more to mix
          MixOrchestratorState mixState = whirlpoolWallet.getState().getMixState();
          if (mixState.getNbIdle() > 0 && !whirlpoolWallet.hasMoreMixableOrUnconfirmed()) {
            // wallet is empty
            log.warn(" • AutoTx0: no Tx0 candidate and we have no more to mix.");
            if (log.isDebugEnabled()) {
              log.debug(
                  "nbIdle="
                      + mixState.getNbIdle()
                      + ", hasMoreMixableOrUnconfirmed="
                      + whirlpoolWallet.hasMoreMixableOrUnconfirmed()
                      + " => empty wallet management");
            }

            // wait tx0Delay before retry
            setLastRun();

            // empty wallet management
            whirlpoolWallet.onEmptyWalletException(e);
          } else {
            // no tx0 possible yet but we may have more to mix
            if (log.isDebugEnabled()) {
              log.debug(
                  " • AutoTx0: no Tx0 candidate yet, but we may have more to mix. nbIdle="
                      + mixState.getNbIdle()
                      + ", hasMoreMixableOrUnconfirmed="
                      + whirlpoolWallet.hasMoreMixableOrUnconfirmed()
                      + " => no empty wallet management");
            }
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
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())
        && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getStatus())
        && whirlpoolUtxo.getUtxoConfig().getPoolId() != null) {

      if (whirlpoolUtxo.getUtxo().confirmations >= WhirlpoolWallet.TX0_MIN_CONFIRMATIONS) {
        log.info(" o AutoTx0: new DEPOSIT utxo detected, checking for tx0: " + whirlpoolUtxo);
        notifyOrchestrator();
      } else {
        log.info(
            " o AutoTx0: new DEPOSIT utxo detected, waiting for confirmation: " + whirlpoolUtxo);
      }
    }
  }

  public void onUtxoConfirmed(WhirlpoolUtxo whirlpoolUtxo) {
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())
        && WhirlpoolUtxoStatus.READY.equals(whirlpoolUtxo.getStatus())
        && whirlpoolUtxo.getUtxo().confirmations >= WhirlpoolWallet.TX0_MIN_CONFIRMATIONS) {
      log.info(" o AutoTx0: new DEPOSIT utxo CONFIRMED, checking for tx0: " + whirlpoolUtxo);
      notifyOrchestrator();
    }
  }
}
