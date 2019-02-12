package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.api.client.SamouraiApi;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoPriorityComparator;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Collection;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTx0Orchestrator extends AbstractOrchestrator {
  private static final Logger log = LoggerFactory.getLogger(AutoTx0Orchestrator.class);

  private Tx0Service tx0Service;
  private SamouraiApi samouraiApi;
  private WhirlpoolWallet whirlpoolWallet;
  private MixOrchestrator mixOrchestrator;
  private int nbOutputsPreferred;

  public AutoTx0Orchestrator(
      Tx0Service tx0Service,
      SamouraiApi samouraiApi,
      WhirlpoolWallet whirlpoolWallet,
      MixOrchestrator mixOrchestrator,
      int loopDelay,
      int nbOutputsPreferred) {
    super(loopDelay);
    this.tx0Service = tx0Service;
    this.samouraiApi = samouraiApi;
    this.whirlpoolWallet = whirlpoolWallet;
    this.mixOrchestrator = mixOrchestrator;
    this.nbOutputsPreferred = nbOutputsPreferred;
  }

  @Override
  protected void runOrchestrator() {
    if (!mixOrchestrator.isSleeping()) {
      try {
        int missingMustMixUtxos = whirlpoolWallet.getState().getMixState().getNbIdle();
        if (missingMustMixUtxos > 0) {
          if (log.isDebugEnabled()) {
            log.info("AutoTx0: preparing for " + missingMustMixUtxos + " Tx0s...");
          }
          // not enough mustMixUtxos => Tx0
          for (int i = 0; i < missingMustMixUtxos; i++) {
            log.info(" • Tx0 (" + (i + 1) + "/" + missingMustMixUtxos + ")...");
            tx0();
          }
        }
      } catch (EmptyWalletException e) {
        whirlpoolWallet.onEmptyWalletException(e);
      } catch (Exception e) {
        log.error("", e);
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("AutoTx0: skipping (MixOrchestrator is sleeping)");
      }
    }
  }

  private void tx0() throws Exception {
    Collection<Pool> poolsByPriority = whirlpoolWallet.getPools().getPools();
    int feeSatPerByte = samouraiApi.fetchFees();
    int nbOutputsMin = 1;

    Collection<WhirlpoolUtxo> depositUtxosByPriority =
        StreamSupport.stream(whirlpoolWallet.getUtxosDeposit(true))
            .sorted(new WhirlpoolUtxoPriorityComparator())
            .collect(Collectors.<WhirlpoolUtxo>toList());

    // find utxo to spend from
    WhirlpoolUtxo spendFrom =
        tx0Service.findSpendFrom(
            depositUtxosByPriority,
            poolsByPriority,
            feeSatPerByte,
            nbOutputsPreferred,
            nbOutputsMin); // throws EmptyWalletException

    // run TX0
    whirlpoolWallet.tx0(spendFrom.getPool(), nbOutputsPreferred, spendFrom);
  }
}
