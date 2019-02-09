package com.samourai.whirlpool.client.wallet.beans;

public enum WhirlpoolUtxoStatus {
  READY,

  TXO,
  TXO_SUCCESS,

  MIX_QUEUE,
  MIX_STARTED,
  MIX_SUCCESS,
  MIX_FAILED;
}
