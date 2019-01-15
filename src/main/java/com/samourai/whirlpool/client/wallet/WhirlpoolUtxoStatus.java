package com.samourai.whirlpool.client.wallet;

public enum WhirlpoolUtxoStatus {
  // ready to TX0
  TXO,

  // ready to mix
  PREMIX,

  // done
  POSTMIX,

  // error
  ERROR;
}
