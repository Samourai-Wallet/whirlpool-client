package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import io.reactivex.subjects.Subject;

public interface WhirlpoolClientListener {
  void success(MixSuccess mixSuccess);

  void fail(MixFailReason reason, String notifiableError);

  void progress(MixStep step);

  Subject<MixProgress> getObservable();
}
