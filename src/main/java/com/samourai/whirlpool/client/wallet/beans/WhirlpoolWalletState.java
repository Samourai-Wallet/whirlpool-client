package com.samourai.whirlpool.client.wallet.beans;

public class WhirlpoolWalletState {
  private boolean started;
  private MixOrchestratorState mixState;

  public WhirlpoolWalletState(boolean started, MixOrchestratorState mixState) {
    this.started = started;
    this.mixState = mixState;
  }

  public boolean isStarted() {
    return started;
  }

  public MixOrchestratorState getMixState() {
    return mixState;
  }
}
