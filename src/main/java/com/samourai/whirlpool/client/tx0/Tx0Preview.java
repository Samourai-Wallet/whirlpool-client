package com.samourai.whirlpool.client.tx0;

import com.samourai.whirlpool.client.whirlpool.beans.Tx0Data;

public class Tx0Preview {
  private Tx0Data tx0Data;
  private long minerFee;
  private long feeValue;
  private long feeChange;
  private int feeDiscountPercent;
  private long premixValue;
  private long changeValue;
  private int nbPremix;

  public Tx0Preview(Tx0Preview tx0Preview) {
    this(
        tx0Preview.tx0Data,
        tx0Preview.minerFee,
        tx0Preview.premixValue,
        tx0Preview.changeValue,
        tx0Preview.nbPremix);
  }

  public Tx0Preview(
      Tx0Data tx0Data, long minerFee, long premixValue, long changeValue, int nbPremix) {
    this.tx0Data = tx0Data;
    this.minerFee = minerFee;
    this.feeValue = tx0Data.getFeeValue();
    this.feeChange = tx0Data.getFeeChange();
    this.feeDiscountPercent = tx0Data.getFeeDiscountPercent();
    this.premixValue = premixValue;
    this.changeValue = changeValue;
    this.nbPremix = nbPremix;
  }

  public long computeFeeValueOrFeeChange() {
    return tx0Data.computeFeeValueOrFeeChange();
  }

  protected Tx0Data getTx0Data() {
    return tx0Data;
  }

  public long getMinerFee() {
    return minerFee;
  }

  public long getFeeValue() {
    return feeValue;
  }

  public long getFeeChange() {
    return feeChange;
  }

  public int getFeeDiscountPercent() {
    return feeDiscountPercent;
  }

  public long getPremixValue() {
    return premixValue;
  }

  public long getChangeValue() {
    return changeValue;
  }

  public int getNbPremix() {
    return nbPremix;
  }

  @Override
  public String toString() {
    return "minerFee="
        + minerFee
        + ", feeValue="
        + feeValue
        + ", feeChange="
        + feeChange
        + ", feeDiscountPercent="
        + feeDiscountPercent
        + ", premixValue="
        + premixValue
        + ", changeValue="
        + changeValue
        + ", nbPremix="
        + nbPremix;
  }
}
