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
  public void success(int nbMixs, MixSuccess mixSuccess) {
    super.success(nbMixs, mixSuccess);
  }

  @Override
  public void fail(int currentMix, int nbMixs) {
    super.fail(currentMix, nbMixs);
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_FAILED);
    whirlpoolUtxo.setError("Mix failed");
  }

  @Override
  public void progress(
      int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
    super.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
    whirlpoolUtxo.setMessage(stepInfo);

    int progressPercent = Math.round(stepNumber * 100 / nbSteps);
    whirlpoolUtxo.setProgress(progressPercent, step.name());
  }

  @Override
  public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
    super.mixSuccess(currentMix, nbMixs, mixSuccess);
    whirlpoolUtxo.setMessage(
        "Funds sent to "
            + mixSuccess.getReceiveAddress()
            + ", txid:"
            + mixSuccess.getReceiveUtxo().getHash());

    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_SUCCESS, 100);
    whirlpoolUtxo.getUtxoConfig().incrementMixsDone();
  }
}
