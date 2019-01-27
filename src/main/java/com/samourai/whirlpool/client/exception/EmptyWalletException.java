package com.samourai.whirlpool.client.exception;

public class EmptyWalletException extends Exception {
  private long balanceRequired;

  public EmptyWalletException(String message, long balanceRequired) {
    super(message);
    this.balanceRequired = balanceRequired;
  }

  public long getBalanceRequired() {
    return balanceRequired;
  }
}
