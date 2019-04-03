package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;

public interface WhirlpoolClientListener {
  void success(MixSuccess mixSuccess);

  void fail();

  void progress(MixStep step, String stepInfo, int stepNumber, int nbSteps);
}
