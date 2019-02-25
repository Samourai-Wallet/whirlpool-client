package com.samourai.whirlpool.client.whirlpool.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public class Pool {
  private String poolId;
  private long denomination;
  private long mustMixBalanceMin;
  private long mustMixBalanceMax;
  private int minAnonymitySet;
  private int nbRegistered;

  private int mixAnonymitySet;
  private MixStatus mixStatus;
  private long elapsedTime;
  private int nbConfirmed;

  public Pool() {}

  public boolean checkInputBalance(long inputBalance, boolean liquidity) {
    long minBalance = computeInputBalanceMin(liquidity);
    long maxBalance = computeInputBalanceMax(liquidity);
    return inputBalance >= minBalance && inputBalance <= maxBalance;
  }

  public long computeInputBalanceMin(boolean liquidity) {
    return WhirlpoolProtocol.computeInputBalanceMin(denomination, mustMixBalanceMin, liquidity);
  }

  public long computeInputBalanceMax(boolean liquidity) {
    return WhirlpoolProtocol.computeInputBalanceMax(denomination, mustMixBalanceMax, liquidity);
  }

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

  public long getMustMixBalanceMin() {
    return mustMixBalanceMin;
  }

  public void setMustMixBalanceMin(long mustMixBalanceMin) {
    this.mustMixBalanceMin = mustMixBalanceMin;
  }

  public long getMustMixBalanceMax() {
    return mustMixBalanceMax;
  }

  public void setMustMixBalanceMax(long mustMixBalanceMax) {
    this.mustMixBalanceMax = mustMixBalanceMax;
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

  public int getNbConfirmed() {
    return nbConfirmed;
  }

  public void setNbConfirmed(int nbConfirmed) {
    this.nbConfirmed = nbConfirmed;
  }
}
