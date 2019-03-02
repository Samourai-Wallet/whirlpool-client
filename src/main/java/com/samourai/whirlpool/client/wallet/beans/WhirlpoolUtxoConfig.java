package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;

public class WhirlpoolUtxoConfig {
  private static final int MIXS_TARGET_DEFAULT = 1;
  private static final int PRIORITY_DEFAULT = 5;

  private Pool pool;
  private int mixsTarget;
  private int priority;

  public WhirlpoolUtxoConfig() {
    this(null, MIXS_TARGET_DEFAULT, PRIORITY_DEFAULT);
  }

  public WhirlpoolUtxoConfig(Pool pool, int mixsTarget, int priority) {
    this.pool = pool;
    this.mixsTarget = mixsTarget;
    this.priority = priority;
  }

  public Pool getPool() {
    return pool;
  }

  public void setPool(Pool pool) {
    this.pool = pool;
  }

  public int getMixsTarget() {
    return mixsTarget;
  }

  public void setMixsTarget(int mixsTarget) {
    this.mixsTarget = mixsTarget;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  @Override
  public String toString() {
    return (pool != null ? "poolId=" + pool.getPoolId() : "")
        + ", mixsTarget="
        + mixsTarget
        + ", priority="
        + priority;
  }
}
