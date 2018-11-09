package com.samourai.whirlpool.client.whirlpool.beans;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public class Pool {
  private String poolId;
  private long denomination;
  private long minerFeeMin;
  private long minerFeeMax;
  private int minAnonymitySet;
  private int nbRegistered;

  private int mixAnonymitySet;
  private MixStatus mixStatus;
  private long elapsedTime;
  private int mixNbConfirmed;

  public Pool() {}

  public String getPoolId() {
    return poolId;
  }

  public void setPoolId(String poolId) {
    this.poolId = poolId;
  }

  public long getDenomination() {
    return denomination;
  }

  public void setDenomination(long denomination) {
    this.denomination = denomination;
  }

  public long getMinerFeeMin() {
    return minerFeeMin;
  }

  public void setMinerFeeMin(long minerFeeMin) {
    this.minerFeeMin = minerFeeMin;
  }

  public long getMinerFeeMax() {
    return minerFeeMax;
  }

  public void setMinerFeeMax(long minerFeeMax) {
    this.minerFeeMax = minerFeeMax;
  }

  public int getMinAnonymitySet() {
    return minAnonymitySet;
  }

  public void setMinAnonymitySet(int minAnonymitySet) {
    this.minAnonymitySet = minAnonymitySet;
  }

  public int getNbRegistered() {
    return nbRegistered;
  }

  public void setNbRegistered(int nbRegistered) {
    this.nbRegistered = nbRegistered;
  }

  public int getMixAnonymitySet() {
    return mixAnonymitySet;
  }

  public void setMixAnonymitySet(int mixAnonymitySet) {
    this.mixAnonymitySet = mixAnonymitySet;
  }

  public MixStatus getMixStatus() {
    return mixStatus;
  }

  public void setMixStatus(MixStatus mixStatus) {
    this.mixStatus = mixStatus;
  }

  public long getElapsedTime() {
    return elapsedTime;
  }

  public void setElapsedTime(long elapsedTime) {
    this.elapsedTime = elapsedTime;
  }

  public int getMixNbConfirmed() {
    return mixNbConfirmed;
  }

  public void setMixNbConfirmed(int mixNbConfirmed) {
    this.mixNbConfirmed = mixNbConfirmed;
  }
}
