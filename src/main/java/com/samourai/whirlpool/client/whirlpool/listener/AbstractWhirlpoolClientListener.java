package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

public abstract class AbstractWhirlpoolClientListener implements WhirlpoolClientListener {
  private WhirlpoolClientListener notifyListener;
  private Subject<MixProgress> observable;

  public AbstractWhirlpoolClientListener(WhirlpoolClientListener notifyListener) {
    this.notifyListener = notifyListener;
    this.observable = BehaviorSubject.create();
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

  public Subject<MixProgress> getObservable() {
    return observable;
  }
}
