package com.samourai.whirlpool.client.tx0;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletAccount;

public class Tx0Config {
  private Integer maxOutputs;
  private WhirlpoolWalletAccount changeWallet;
  private Long overspend;

  public Tx0Config() {
    this.maxOutputs = null; // spend whole utxo when possible
    this.changeWallet = WhirlpoolWalletAccount.DEPOSIT;
    this.overspend = null;
  }

  public Integer getMaxOutputs() {
    return maxOutputs;
  }

  public Tx0Config setMaxOutputs(Integer maxOutputs) {
    this.maxOutputs = maxOutputs;
    return this;
  }

  public WhirlpoolWalletAccount getChangeWallet() {
    return changeWallet;
  }

  public Tx0Config setChangeWallet(WhirlpoolWalletAccount changeWallet) {
    this.changeWallet = changeWallet;
    return this;
  }

  public Long getOverspend() {
    return overspend;
  }

  public void setOverspend(Long overspend) {
    this.overspend = overspend;
  }
}
