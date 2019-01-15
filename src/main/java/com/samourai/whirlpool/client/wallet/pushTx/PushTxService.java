package com.samourai.whirlpool.client.wallet.pushTx;

import org.bitcoinj.core.Transaction;

public interface PushTxService {
  boolean testConnectivity();

  void pushTx(String txHex) throws Exception;

  void pushTx(Transaction tx) throws Exception;
}
