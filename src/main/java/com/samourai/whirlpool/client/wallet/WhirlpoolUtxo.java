package com.samourai.whirlpool.client.wallet;

public interface WhirlpoolUtxo {

  String getHash();

  int getIndex();

  WhirlpoolUtxoPriority getPriority();

  WhirlpoolUtxoState getState();
}
