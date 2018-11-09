package com.samourai.whirlpool.client.mix.listener;

import com.samourai.whirlpool.client.mix.MixParams;

public interface MixClientListener {
  void success(MixSuccess mixSuccess, MixParams nextMixParams);

  void fail();

  void progress(MixStep step, String stepInfo, int stepNumber, int nbSteps);
}
