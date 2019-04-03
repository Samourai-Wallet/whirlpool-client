package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;

public class UtxoWhirlpoolClientListener extends AbstractWhirlpoolClientListener {
  private WhirlpoolUtxo whirlpoolUtxo;

  public UtxoWhirlpoolClientListener(
      WhirlpoolClientListener notifyListener, WhirlpoolUtxo whirlpoolUtxo) {
    super(notifyListener);
    this.whirlpoolUtxo = whirlpoolUtxo;
  }

  public UtxoWhirlpoolClientListener(WhirlpoolUtxo whirlpoolUtxo) {
    super();
    this.whirlpoolUtxo = whirlpoolUtxo;
  }

  @Override
  public void success(MixSuccess mixSuccess) {
    super.success(mixSuccess);
    whirlpoolUtxo.setMessage("txid: " + mixSuccess.getReceiveUtxo().getHash());
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_SUCCESS, 100);
    whirlpoolUtxo.getUtxoConfig().incrementMixsDone();
  }

  @Override
  public void fail() {
    super.fail();
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_FAILED);
    whirlpoolUtxo.setError("Mix failed");
  }

  @Override
  public void progress(MixStep step, String stepInfo, int stepNumber, int nbSteps) {
    super.progress(step, stepInfo, stepNumber, nbSteps);
    whirlpoolUtxo.setMessage(stepInfo);

    int progressPercent = Math.round(stepNumber * 100 / nbSteps);
    whirlpoolUtxo.setProgress(progressPercent, step.name());
  }
}
