package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
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
  public void success(MixSuccess mixSuccess) {
    if (notifyListener != null) {
      notifyListener.success(mixSuccess);
    }
  }

  @Override
  public void fail(MixFailReason reason, String notifiableError) {
    if (notifyListener != null) {
      notifyListener.fail(reason, notifiableError);
    }
  }

  @Override
  public void progress(MixStep step) {
    if (notifyListener != null) {
      notifyListener.progress(step);
    }
  }
}
