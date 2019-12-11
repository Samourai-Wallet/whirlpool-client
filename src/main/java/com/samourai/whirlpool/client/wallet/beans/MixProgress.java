package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.mix.listener.MixStep;

public class MixProgress {
  private MixStep mixStep;
  private int progressPercent;

  public MixProgress(MixStep mixStep) {
    this.mixStep = mixStep;
    this.progressPercent = mixStep.getProgress();
  }

  public MixStep getMixStep() {
    return mixStep;
  }

  public int getProgressPercent() {
    return progressPercent;
  }

  @Override
  public String toString() {
    return "(" + progressPercent + "%: " + mixStep;
  }
}
