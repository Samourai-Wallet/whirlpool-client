package com.samourai.whirlpool.client.exception;

import com.samourai.whirlpool.client.utils.ClientUtils;

public class EmptyWalletException extends Exception {
  private long balanceRequired;

  public EmptyWalletException(String message, long balanceRequired) {
    super(message);
    this.balanceRequired = balanceRequired;
  }

  public long getBalanceRequired() {
    return balanceRequired;
  }

  public String getMessageDeposit(String depositAddress) {
    return "Insufficient balance to continue. I need at least "
        + ClientUtils.satToBtc(balanceRequired)
        + "btc to continue.\nPlease make a deposit to "
        + depositAddress
        + ".\nCaused by:"
        + getMessage();
  }
}
