package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.protocol.beans.Utxo;

public class MixProgressSuccess extends MixProgress {
  private String receiveAddress;
  private Utxo receiveUtxo;

  public MixProgressSuccess(String receiveAddress, Utxo receiveUtxo) {
    super(MixStep.SUCCESS);
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
