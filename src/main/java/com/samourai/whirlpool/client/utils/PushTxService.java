package com.samourai.whirlpool.client.utils;

import org.bitcoinj.core.Transaction;

public interface PushTxService {

  void pushTx(String txHex) throws Exception;

  void pushTx(Transaction tx) throws Exception;
}
