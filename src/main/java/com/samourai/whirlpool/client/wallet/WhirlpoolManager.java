package com.samourai.whirlpool.client.wallet;

import java.util.List;

public class WhirlpoolManager {

  public void start() {}

  public void stop() {}

  public boolean isStarted() {
    return false;
  }

  public void clear() {}

  // resume from premix/postmix
  public WhirlpoolUtxo add(String utxoHash, int utxoIndex, WhirlpoolUtxoPriority priority) {
    return null;
  }

  public WhirlpoolUtxo add(String utxoHash, int utxoIndex) {
    return null;
  }

  public void setPriority(WhirlpoolUtxo utxo, WhirlpoolUtxoPriority priority) {}

  public List<WhirlpoolUtxo> getList(WhirlpoolUtxoStatus... statuses) {
    return null;
  }

  public void remove(WhirlpoolUtxo whirlpoolUtxo) {}
}
