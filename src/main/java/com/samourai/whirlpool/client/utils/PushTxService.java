package com.samourai.whirlpool.client.utils;

import org.bitcoinj.core.Transaction;

public interface PushTxService {

  void pushTx(Transaction tx);
}
