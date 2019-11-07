package com.samourai.whirlpool.client.utils;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.bip69.BIP69InputComparatorGeneric;

public class BIP69InputComparatorUnspentOutput
    extends BIP69InputComparatorGeneric<UnspentResponse.UnspentOutput> {
  @Override
  protected long getIndex(UnspentResponse.UnspentOutput i) {
    return i.tx_output_n;
  }

  @Override
  protected byte[] getHash(UnspentResponse.UnspentOutput i) {
    return i.tx_hash.getBytes();
  }
}
