package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;

public class UtxoWhirlpoolClientListener extends AbstractWhirlpoolClientListener {
  private WhirlpoolUtxo whirlpoolUtxo;
  private WhirlpoolWallet whirlpoolWallet;

  public UtxoWhirlpoolClientListener(
      WhirlpoolClientListener notifyListener,
      WhirlpoolUtxo whirlpoolUtxo,
      WhirlpoolWallet whirlpoolWallet) {
    super(notifyListener);
    this.whirlpoolUtxo = whirlpoolUtxo;
    this.whirlpoolWallet = whirlpoolWallet;
  }

  public UtxoWhirlpoolClientListener(WhirlpoolUtxo whirlpoolUtxo, WhirlpoolWallet whirlpoolWallet) {
    this(null, whirlpoolUtxo, whirlpoolWallet);
  }

  @Override
  public void success(MixSuccess mixSuccess) {
    super.success(mixSuccess);
    whirlpoolUtxo.setMessage("txid: " + mixSuccess.getReceiveUtxo().getHash());
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_SUCCESS, 100);
    whirlpoolUtxo.getUtxoConfig().incrementMixsDone();

    // notify
    whirlpoolWallet.onMixSuccess(mixSuccess, whirlpoolUtxo);
  }

  @Override
  public void fail(MixFailReason reason, String notifiableError) {
    super.fail(reason, notifiableError);
    String message = reason.getMessage();
    if (notifiableError != null) {
      message += " ; " + notifiableError;
    }
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_FAILED);
    whirlpoolUtxo.setError(message);
  }

  @Override
  public void progress(MixStep step) {
    super.progress(step);
    whirlpoolUtxo.setMessage(step.getMessage());
    whirlpoolUtxo.setStatus(whirlpoolUtxo.getStatus(), step, step.getProgress());
  }
}
