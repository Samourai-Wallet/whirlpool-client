package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;

public interface WhirlpoolClientListener {
  void success(int nbMixs, MixSuccess mixSuccess);

  void fail(int currentMix, int nbMixs);

  void progress(
      int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps);

  void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess);
}
