package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;

public abstract class AbstractWhirlpoolClientListener implements WhirlpoolClientListener {
  private WhirlpoolClientListener notifyListener;

  public AbstractWhirlpoolClientListener(WhirlpoolClientListener notifyListener) {
    this.notifyListener = notifyListener;
  }

  public AbstractWhirlpoolClientListener() {
    this(null);
  }

  @Override
  public void success(int nbMixs, MixSuccess mixSuccess) {
    if (notifyListener != null) {
      notifyListener.success(nbMixs, mixSuccess);
    }
  }

  @Override
  public void fail(int currentMix, int nbMixs) {
    if (notifyListener != null) {
      notifyListener.fail(currentMix, nbMixs);
    }
  }

  @Override
  public void progress(
      int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
    if (notifyListener != null) {
      notifyListener.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
    }
  }

  @Override
  public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
    if (notifyListener != null) {
      notifyListener.mixSuccess(currentMix, nbMixs, mixSuccess);
    }
  }
}
