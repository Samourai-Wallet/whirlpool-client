package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;

public class WhirlpoolUtxoConfig {
  public static final int MIXS_TARGET_UNLIMITED = 0;
  private WhirlpoolWallet whirlpoolWallet;
  private String poolId;
  private int mixsTarget;
  private int mixsDone;
  private long lastModified;

  public WhirlpoolUtxoConfig(WhirlpoolWallet whirlpoolWallet, int mixsTarget) {
    this(whirlpoolWallet, null, mixsTarget, 0, 0);
  }

  public WhirlpoolUtxoConfig(WhirlpoolUtxoConfig copy) {
    this(
        copy.whirlpoolWallet,
        copy.poolId,
        copy.mixsTarget,
        copy.mixsDone,
        System.currentTimeMillis());
  }

  public WhirlpoolUtxoConfig(
      WhirlpoolWallet whirlpoolWallet,
      String poolId,
      int mixsTarget,
      int mixsDone,
      long lastModified) {
    this.whirlpoolWallet = whirlpoolWallet;
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.mixsDone = mixsDone;
    this.lastModified = lastModified;
  }

  public WhirlpoolUtxoConfig copy() {
    WhirlpoolUtxoConfig copy = new WhirlpoolUtxoConfig(this);
    return copy;
  }

  public String getPoolId() {
    return poolId;
  }

  public void setPoolId(String poolId) {
    this.poolId = poolId;
    whirlpoolWallet.onUtxoConfigChanged(this);
  }

  public int getMixsTarget() {
    return mixsTarget;
  }

  public void setMixsTarget(int mixsTarget) {
    this.mixsTarget = mixsTarget;
    whirlpoolWallet.onUtxoConfigChanged(this);
  }

  public int getMixsDone() {
    return mixsDone;
  }

  public void incrementMixsDone() {
    this.mixsDone++;
    setLastModified();
    whirlpoolWallet.onUtxoConfigChanged(this);
  }

  public long getLastModified() {
    return lastModified;
  }

  private void setLastModified() {
    this.lastModified = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "poolId="
        + (poolId != null ? poolId : "null")
        + ", mixsTarget="
        + mixsTarget
        + ", mixsDone="
        + mixsDone;
  }
}
