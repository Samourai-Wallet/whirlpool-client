package com.samourai.whirlpool.client.tx0;

import com.samourai.wallet.api.backend.beans.UnspentResponse;

public class UnspentOutputWithKey extends UnspentResponse.UnspentOutput {
  private byte[] key;

  public UnspentOutputWithKey(UnspentResponse.UnspentOutput uo, byte[] key) {
    this.tx_hash = uo.tx_hash;
    this.tx_output_n = uo.tx_output_n;
    this.value = uo.value;
    this.script = uo.script;
    this.addr = uo.addr;
    this.confirmations = uo.confirmations;
    this.xpub = uo.xpub;
    this.key = key;
  }

  byte[] getKey() {
    return key;
  }
}
