package com.samourai.whirlpool.client.wallet.pushTx;

import org.bitcoinj.core.Transaction;

public abstract class AbstractPushTxService implements PushTxService {

  @Override
  public void pushTx(Transaction tx) throws Exception {
    String txHex = org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize());
    pushTx(txHex);
  }
}
