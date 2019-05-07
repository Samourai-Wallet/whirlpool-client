package com.samourai.whirlpool.client.mix.listener;

import com.samourai.whirlpool.client.mix.MixParams;

public interface MixClientListener {
  void success(MixSuccess mixSuccess, MixParams nextMixParams);

  void fail(MixFailReason reason, String notifiableError);

  void progress(MixStep step);
}
