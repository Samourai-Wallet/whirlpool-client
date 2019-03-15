package com.samourai.whirlpool.client.wallet.beans;

public class WhirlpoolUtxoConfig {
  private String poolId;
  private int mixsTarget;
  private int mixsDone;
  private long lastModified;

  public WhirlpoolUtxoConfig(int mixsTarget) {
    this(null, mixsTarget, 0, 0);
  }

  public WhirlpoolUtxoConfig(String poolId, int mixsTarget, int mixsDone, long lastModified) {
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.mixsDone = mixsDone;
    this.lastModified = lastModified;
  }

  private void set(WhirlpoolUtxoConfig copy, long lastModified) {
    this.poolId = copy.poolId;
    this.mixsTarget = copy.mixsTarget;
    this.mixsDone = copy.mixsDone;
    this.lastModified = lastModified;
  }

  public WhirlpoolUtxoConfig copy() {
    WhirlpoolUtxoConfig copy = new WhirlpoolUtxoConfig(this.mixsTarget);
    long now = System.currentTimeMillis();
    copy.set(this, now);
    return copy;
  }

  public String getPoolId() {
    return poolId;
  }

  public void setPoolId(String poolId) {
    this.poolId = poolId;
    setLastModified();
  }

  public int getMixsTarget() {
    return mixsTarget;
  }

  public void setMixsTarget(int mixsTarget) {
    this.mixsTarget = mixsTarget;
    setLastModified();
  }

  public int getMixsDone() {
    return mixsDone;
  }

  public void incrementMixsDone() {
    this.mixsDone++;
    setLastModified();
  }

  public long getLastModified() {
    return lastModified;
  }

  private void setLastModified() {
    this.lastModified = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "poolId=" + (poolId != null ? poolId : "null") + ", mixsTarget=" + mixsTarget;
  }
}
