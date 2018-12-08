package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import java.util.HashMap;
import java.util.Map;

public class MultiClientListener extends LoggingWhirlpoolClientListener {
  // indice 0 is always null as currentMix starts from 1
  private Map<Integer, MixStatus> mixStatus = new HashMap<Integer, MixStatus>();
  private Map<Integer, MixStep> mixStep = new HashMap<Integer, MixStep>();
  private MultiClientManager multiClientManager;
  private int missedMixs;

  public MultiClientListener(MultiClientManager multiClientManager, int missedMixs) {
    this.missedMixs = missedMixs;
    this.multiClientManager = multiClientManager;
    for (int i = 0; i <= missedMixs; i++) {
      mixStatus.put(i, null);
      mixStep.put(i, null);
    }
  }

  @Override
  public void success(int nbMixs, MixSuccess mixSuccess) {
    super.success(nbMixs, mixSuccess);
    notifyMultiClientManager();
  }

  @Override
  public void progress(
      int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
    super.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
    this.mixStep.put(missedMixs + currentMix, step);
  }

  @Override
  public void fail(int currentMix, int nbMixs) {
    super.fail(currentMix, nbMixs);
    this.mixStatus.put(missedMixs + currentMix, MixStatus.FAIL);
    notifyMultiClientManager();
  }

  @Override
  public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
    super.mixSuccess(currentMix, nbMixs, mixSuccess);
    this.mixStatus.put(missedMixs + currentMix, MixStatus.SUCCESS);
  }

  private void notifyMultiClientManager() {
    synchronized (multiClientManager) {
      multiClientManager.notify();
    }
  }

  public MixStatus getMixStatus(int currentMix) {
    return mixStatus.get(currentMix);
  }

  public MixStep getMixStep(int currentMix) {
    return mixStep.get(currentMix);
  }
}
