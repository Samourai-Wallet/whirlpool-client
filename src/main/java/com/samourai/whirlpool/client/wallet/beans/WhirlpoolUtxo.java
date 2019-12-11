package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;

public class WhirlpoolUtxo {
  private UnspentOutput utxo;
  private WhirlpoolAccount account;
  private WhirlpoolUtxoConfig utxoConfig;
  private WhirlpoolUtxoState utxoState;

  public WhirlpoolUtxo(
      UnspentOutput utxo,
      WhirlpoolAccount account,
      WhirlpoolUtxoConfig utxoConfig,
      WhirlpoolUtxoStatus status) {
    this.utxo = utxo;
    this.account = account;
    this.utxoConfig = utxoConfig;
    this.utxoState = new WhirlpoolUtxoState(status);
  }

  public UnspentOutput getUtxo() {
    return utxo;
  }

  public void setUtxo(UnspentOutput utxo) {
    this.utxo = utxo;
  }

  public WhirlpoolAccount getAccount() {
    return account;
  }

  public WhirlpoolUtxoConfig getUtxoConfig() {
    return utxoConfig;
  }

  public WhirlpoolUtxoState getUtxoState() {
    return utxoState;
  }

  @Override
  public String toString() {
    return utxo.toString() + ": " + utxoState;
  }
}
