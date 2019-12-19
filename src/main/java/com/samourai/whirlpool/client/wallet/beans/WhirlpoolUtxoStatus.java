package com.samourai.whirlpool.client.wallet.beans;

public enum WhirlpoolUtxoStatus {
  READY,
  STOP,

  TX0,
  TX0_FAILED,
  TX0_SUCCESS,

  MIX_QUEUE,
  MIX_STARTED,
  MIX_SUCCESS,
  MIX_FAILED;
}
