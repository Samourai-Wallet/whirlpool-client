package com.samourai.whirlpool.client.wallet.beans;

public enum WhirlpoolWalletAccount {
  DEPOSIT(0),
  BADBANK(Integer.MAX_VALUE - 3),
  PREMIX(Integer.MAX_VALUE - 2),
  POSTMIX(Integer.MAX_VALUE - 1);

  private int accountIndex;

  WhirlpoolWalletAccount(int accountIndex) {
    this.accountIndex = accountIndex;
  }

  public int getAccountIndex() {
    return accountIndex;
  }
}
