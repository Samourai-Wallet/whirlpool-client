package com.samourai.whirlpool.client.wallet.beans;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class WhirlpoolUtxoConfig {
  public static final int MIXS_TARGET_UNLIMITED = 0;
  private String poolId;
  private Integer mixsTarget;
  private int mixsDone;
  private long lastModified;
  private PublishSubject<WhirlpoolUtxoConfig> observable;

  public WhirlpoolUtxoConfig() {
    this(null, null, 0, 0);
  }

  public WhirlpoolUtxoConfig(int mixsDone) {
    this(null, null, mixsDone, 0);
  }

  protected WhirlpoolUtxoConfig(WhirlpoolUtxoConfig copy) {
    this(copy.poolId, copy.mixsTarget, copy.mixsDone, System.currentTimeMillis());
  }

  public WhirlpoolUtxoConfig(String poolId, Integer mixsTarget, int mixsDone, long lastModified) {
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.mixsDone = mixsDone;
    this.lastModified = lastModified;
    this.observable = PublishSubject.create();
  }

  private void emit() {
    // notify observers
    observable.onNext(this);
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
    emit();
  }

  public Integer getMixsTarget() {
    return mixsTarget;
  }

  public int getMixsTargetOrDefault(int mixsTargetMin) {
    if (mixsTarget == null) {
      return mixsTargetMin;
    }
    if (mixsTarget == WhirlpoolUtxoConfig.MIXS_TARGET_UNLIMITED) {
      return WhirlpoolUtxoConfig.MIXS_TARGET_UNLIMITED;
    }
    return Math.max(mixsTarget, mixsTargetMin);
  }

  public void setMixsTarget(Integer mixsTarget) {
    this.mixsTarget = mixsTarget;
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
    emit();
  }

  public Observable<WhirlpoolUtxoConfig> getObservable() {
    return observable;
  }

  @Override
  public String toString() {
    return "poolId="
        + (poolId != null ? poolId : "null")
        + ", mixsTarget="
        + (mixsTarget != null ? mixsTarget : "null")
        + ", mixsDone="
        + mixsDone;
  }
}
