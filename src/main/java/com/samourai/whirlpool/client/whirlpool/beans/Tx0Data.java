package com.samourai.whirlpool.client.whirlpool.beans;

import java.util.*;

public class Tx0Data {
  private String feePaymentCode;
  private byte[] feePayload;
  private String feeAddress;
  private Integer feeIndice;

  public Tx0Data(String feePaymentCode, byte[] feePayload, String feeAddress, int feeIndice) {
    this.feePaymentCode = feePaymentCode;
    this.feePayload = feePayload;
    this.feeAddress = feeAddress;
    this.feeIndice = feeIndice;
  }

  public String getFeePaymentCode() {
    return feePaymentCode;
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
        + ", feePayload="
        + feePayload
        + ", feeAddress="
        + (feeAddress != null ? feeAddress : "null")
        + ", feeIndice="
        + (feeIndice != null ? feeIndice : "null");
  }
}
