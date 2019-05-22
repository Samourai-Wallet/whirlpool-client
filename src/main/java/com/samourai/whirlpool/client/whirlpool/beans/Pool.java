package com.samourai.whirlpool.client.whirlpool.beans;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public class Pool {
  private String poolId;
  private long denomination;
  private long feeValue;
  private long mustMixBalanceMin;
  private long mustMixBalanceCap;
  private long mustMixBalanceMax;
  private int minAnonymitySet;
  private int minMustMix;
  private int nbRegistered;

  private int mixAnonymitySet;
  private MixStatus mixStatus;
  private long elapsedTime;
  private int nbConfirmed;

  public Pool() {}

  public boolean checkInputBalance(long inputBalance, boolean liquidity) {
    long minBalance = computePremixBalanceMin(liquidity);
    long maxBalance = computePremixBalanceMax(liquidity);
    return inputBalance >= minBalance && inputBalance <= maxBalance;
  }

  public long computePremixBalanceMin(boolean liquidity) {
    return WhirlpoolProtocol.computePremixBalanceMin(denomination, mustMixBalanceMin, liquidity);
  }

  public long computePremixBalanceMax(boolean liquidity) {
    return WhirlpoolProtocol.computePremixBalanceMax(denomination, mustMixBalanceMax, liquidity);
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

  public long getFeeValue() {
    return feeValue;
  }

  public void setFeeValue(long feeValue) {
    this.feeValue = feeValue;
  }

  public long getMustMixBalanceMin() {
    return mustMixBalanceMin;
  }

  public void setMustMixBalanceMin(long mustMixBalanceMin) {
    this.mustMixBalanceMin = mustMixBalanceMin;
  }

  public long getMustMixBalanceCap() {
    return mustMixBalanceCap;
  }

  public void setMustMixBalanceCap(long mustMixBalanceCap) {
    this.mustMixBalanceCap = mustMixBalanceCap;
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

  public int getMinMustMix() {
    return minMustMix;
  }

  public void setMinMustMix(int minMustMix) {
    this.minMustMix = minMustMix;
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
