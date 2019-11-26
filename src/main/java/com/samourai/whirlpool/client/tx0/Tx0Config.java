package com.samourai.whirlpool.client.tx0;

public class Tx0Config {
  private Integer maxOutputs;
  private boolean badbankChange;

  public Tx0Config() {}

  public Integer getMaxOutputs() {
    return maxOutputs;
  }

  public Tx0Config setMaxOutputs(Integer maxOutputs) {
    this.maxOutputs = maxOutputs;
    return this;
  }

  public boolean isBadbankChange() {
    return badbankChange;
  }

  public Tx0Config setBadbankChange(boolean badbankChange) {
    this.badbankChange = badbankChange;
    return this;
  }
}
