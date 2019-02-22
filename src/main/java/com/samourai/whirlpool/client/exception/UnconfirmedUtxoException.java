package com.samourai.whirlpool.client.exception;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;

public class UnconfirmedUtxoException extends NotifiableException {
  private UnspentOutput utxo;

  public UnconfirmedUtxoException(UnspentOutput utxo) {
    super("Utxo is unconfirmed: " + utxo);
    this.utxo = utxo;
  }

  public UnspentOutput getUtxo() {
    return utxo;
  }
}
