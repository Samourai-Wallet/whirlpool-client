package com.samourai.whirlpool.client.wallet.beans;

public class WhirlpoolUtxoConfig {
  private static final int PRIORITY_DEFAULT = 5;

  private String poolId;
  private int mixsTarget;
  private int priority;
  private int mixsDone;
  private long lastModified;
  private long lastSeen;

  public WhirlpoolUtxoConfig(int mixsTarget) {
    this(null, mixsTarget, PRIORITY_DEFAULT, 0, System.currentTimeMillis());
  }

  public WhirlpoolUtxoConfig(
      String poolId, int mixsTarget, int priority, long lastModified, long lastSeen) {
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.priority = priority;
    this.mixsDone = 0;
    this.lastModified = lastModified;
    this.lastSeen = lastSeen;
  }

  private void set(WhirlpoolUtxoConfig copy, long lastModified, long lastSeen) {
    this.poolId = copy.poolId;
    this.mixsTarget = copy.mixsTarget;
    this.priority = copy.priority;
    this.mixsDone = copy.mixsDone;
    this.lastModified = lastModified;
    this.lastSeen = lastSeen;
  }

  public WhirlpoolUtxoConfig copy() {
    WhirlpoolUtxoConfig copy = new WhirlpoolUtxoConfig(this.mixsTarget);
    long now = System.currentTimeMillis();
    copy.set(this, now, now);
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

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
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

  public long getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen() {
    this.lastSeen = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return "poolId="
        + (poolId != null ? poolId : "null")
        + ", mixsTarget="
        + mixsTarget
        + ", priority="
        + priority;
  }
}
