package com.samourai.whirlpool.client.mix.listener;

import com.samourai.whirlpool.protocol.beans.Utxo;

public class MixSuccess {
  private String receiveAddress;
  private Utxo receiveUtxo;

  public MixSuccess(String receiveAddress, Utxo receiveUtxo) {
    this.receiveAddress = receiveAddress;
    this.receiveUtxo = receiveUtxo;
  }

  public String getReceiveAddress() {
    return receiveAddress;
  }

  public Utxo getReceiveUtxo() {
    return receiveUtxo;
  }
}
