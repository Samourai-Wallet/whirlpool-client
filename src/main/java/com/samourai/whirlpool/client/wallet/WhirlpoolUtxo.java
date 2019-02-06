package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;

public class WhirlpoolUtxo {
  private UnspentOutput utxo;
  private WhirlpoolUtxoStatus status;

  public WhirlpoolUtxo(UnspentOutput utxo, WhirlpoolUtxoStatus status) {
    this.utxo = utxo;
    this.status = status;
  }

  public UnspentOutput getUtxo() {
    return utxo;
  }

  public WhirlpoolUtxoStatus getStatus() {
    return status;
  }
}
