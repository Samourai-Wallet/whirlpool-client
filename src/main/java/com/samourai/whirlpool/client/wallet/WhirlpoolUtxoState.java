package com.samourai.whirlpool.client.wallet;

public interface WhirlpoolUtxoState {
  int MIXS_UNLIMITED = -1;

  WhirlpoolUtxoStatus getStatus();

  int getNbSuccess();

  int getNbError();

  int getNbRemaining();

  int getNbTarget();

  String getLastError();
}
