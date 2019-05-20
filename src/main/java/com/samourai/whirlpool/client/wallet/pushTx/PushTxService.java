package com.samourai.whirlpool.client.wallet.pushTx;

public interface PushTxService {
  boolean testConnectivity();

  void pushTx(String txHex) throws Exception;
}
