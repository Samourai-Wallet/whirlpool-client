package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoChanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTx0Orchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(AutoTx0Orchestrator.class);
  // start delay to make sure UTXOS are refreshed when starting
  private static final int START_DELAY = 15000;

  private WhirlpoolWallet whirlpoolWallet;
  private String autoTx0PoolId;

  public AutoTx0Orchestrator(
      int loopDelay, WhirlpoolWallet whirlpoolWallet, int tx0Delay, String autoTx0PoolId) {
    super(loopDelay, START_DELAY, tx0Delay);
    this.whirlpoolWallet = whirlpoolWallet;
    this.autoTx0PoolId = autoTx0PoolId;
  }

  @Override
  protected void resetOrchestrator() {
    super.resetOrchestrator();
  }

  @Override
  protected void runOrchestrator() {
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
    } catch (EmptyWalletException e) {
      // make sure that mixOrchestrator has no more to mix
      boolean hasMoreThreadForTx0 = whirlpoolWallet.hasMoreMixingThreadAvailable(autoTx0PoolId);
      boolean hasMorMixableOrUnconfirmed = whirlpoolWallet.hasMoreMixableOrUnconfirmed();
      if (hasMoreThreadForTx0 && !hasMorMixableOrUnconfirmed) {
        // wallet is empty
        log.warn(" • AutoTx0: no Tx0 candidate and we have no more to mix.");
        if (log.isDebugEnabled()) {
          log.debug(
              "hasMoreThreadForTx0="
                  + hasMoreThreadForTx0
                  + ", hasMoreMixableOrUnconfirmed="
                  + hasMorMixableOrUnconfirmed
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
              " • AutoTx0: no Tx0 candidate yet, but we may have more to mix. hasMoreThreadForTx0="
                  + hasMoreThreadForTx0
                  + ", hasMoreMixableOrUnconfirmed="
                  + hasMorMixableOrUnconfirmed
                  + " => no empty wallet management");
        }
      }
    } catch (Exception e) {
      log.error("", e);
    }

    // no tx0 can be made now, check back later...
  }

  public void onUtxoChanges(WhirlpoolUtxoChanges whirlpoolUtxoChanges) {
    boolean notify = false;

    // DETECTED
    for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxoChanges.getUtxosDetected()) {
      if (whirlpoolUtxo.getUtxo().confirmations
          >= whirlpoolWallet.getConfig().getTx0MinConfirmations()) {
        notify = true;
      }
    }

    // UPDATED
    for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxoChanges.getUtxosDetected()) {
      if (whirlpoolUtxo.getUtxo().confirmations
          >= whirlpoolWallet.getConfig().getTx0MinConfirmations()) {
        notify = true;
      }
    }

    if (notify) {
      if (log.isDebugEnabled()) {
        log.debug(" o AutoTx0: checking for tx0...");
      }
      notifyOrchestrator();
    }
  }
}
