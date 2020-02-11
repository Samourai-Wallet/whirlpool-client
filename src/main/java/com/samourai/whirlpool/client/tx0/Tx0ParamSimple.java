package com.samourai.whirlpool.client.tx0;

public class Tx0ParamSimple {
  private int feeTx0;
  private int feePremix;

  public Tx0ParamSimple(int feeTx0, int feePremix) {
    this.feeTx0 = feeTx0;
    this.feePremix = feePremix;
  }

  public Tx0ParamSimple(Tx0ParamSimple copy) {
    this(copy.feeTx0, copy.feePremix);
  }

  public int getFeeTx0() {
    return feeTx0;
  }

  public int getFeePremix() {
    return feePremix;
  }

  @Override
  public String toString() {
    return "feeTx0=" + feeTx0 + ", feePremix=" + feePremix;
  }
}
