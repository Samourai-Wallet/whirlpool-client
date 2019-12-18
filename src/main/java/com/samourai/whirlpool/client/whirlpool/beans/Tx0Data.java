package com.samourai.whirlpool.client.whirlpool.beans;

public class Tx0Data {
  private String feePaymentCode;
  private long feeValue;
  private long feeChange;
  private int feeDiscountPercent;
  private byte[] feePayload;
  private String feeAddress;
  private Integer feeIndice;

  public Tx0Data(
      String feePaymentCode,
      long feeValue,
      long feeChange,
      int feeDiscountPercent,
      byte[] feePayload,
      String feeAddress,
      Integer feeIndice) {
    this.feePaymentCode = feePaymentCode;
    this.feeValue = feeValue;
    this.feeChange = feeChange;
    this.feeDiscountPercent = feeDiscountPercent;
    this.feePayload = feePayload;
    this.feeAddress = feeAddress;
    this.feeIndice = feeIndice;
  }

  public long computeFeeValueOrFeeChange() {
    return feeValue > 0 ? feeValue : feeChange;
  }

  public String getFeePaymentCode() {
    return feePaymentCode;
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

  public byte[] getFeePayload() {
    return feePayload;
  }

  public String getFeeAddress() {
    return feeAddress;
  }

  public Integer getFeeIndice() {
    return feeIndice;
  }

  @Override
  public String toString() {
    return "feePaymentCode="
        + feePaymentCode
        + ", feeValue="
        + feeValue
        + ", feeChange="
        + feeChange
        + ", feeDiscountPercent="
        + feeDiscountPercent
        + ", feePayload="
        + feePayload
        + ", feeAddress="
        + (feeAddress != null ? feeAddress : "null")
        + ", feeIndice="
        + (feeIndice != null ? feeIndice : "null");
  }
}
