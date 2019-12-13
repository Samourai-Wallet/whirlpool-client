package com.samourai.whirlpool.client.wallet.beans;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class WhirlpoolUtxoConfig {
  public static final int MIXS_TARGET_UNLIMITED = 0;
  private String poolId;
  private int mixsTarget;
  private int mixsDone;
  private long lastModified;
  private PublishSubject<WhirlpoolUtxoConfig> observable;

  public WhirlpoolUtxoConfig(int mixsTarget) {
    this(null, mixsTarget, 0, 0);
  }

  public WhirlpoolUtxoConfig(int mixsTarget, int mixsDone) {
    this(null, mixsTarget, mixsDone, 0);
  }

  protected WhirlpoolUtxoConfig(WhirlpoolUtxoConfig copy) {
    this(copy.poolId, copy.mixsTarget, copy.mixsDone, System.currentTimeMillis());
  }

  public WhirlpoolUtxoConfig(String poolId, int mixsTarget, int mixsDone, long lastModified) {
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

  public int getMixsTarget() {
    return mixsTarget;
  }

  public void setMixsTarget(int mixsTarget) {
    this.mixsTarget = mixsTarget;
    emit();
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
        + getMixsTarget()
        + ", mixsDone="
        + mixsDone;
  }
}
