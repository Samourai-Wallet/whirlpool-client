package com.samourai.whirlpool.client.mix.listener;

public interface MixClientListener {
  void success(MixSuccess mixSuccess);

  void fail(MixFailReason reason, String notifiableError);

  void progress(MixStep step);
}
