package com.samourai.whirlpool.client.exception;

import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;

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
