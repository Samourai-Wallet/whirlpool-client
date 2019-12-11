package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;

public class MixProgressFail extends MixProgress {
  private MixFailReason reason;

  public MixProgressFail(MixFailReason reason) {
    super(MixStep.FAIL);
    this.reason = reason;
  }

  public MixFailReason getReason() {
    return reason;
  }
}
