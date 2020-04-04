package com.samourai.whirlpool.client.wallet.beans;

import java.util.ArrayList;
import java.util.List;

public class WhirlpoolUtxoChanges {
  private boolean isFirstFetch;
  private List<WhirlpoolUtxo> utxosDetected;
  private List<WhirlpoolUtxo> utxosUpdated;
  private List<WhirlpoolUtxo> utxosRemoved;

  public WhirlpoolUtxoChanges(boolean isFirstFetch) {
    this.isFirstFetch = isFirstFetch;
    this.utxosDetected = new ArrayList<WhirlpoolUtxo>();
    this.utxosUpdated = new ArrayList<WhirlpoolUtxo>();
    this.utxosRemoved = new ArrayList<WhirlpoolUtxo>();
  }

  public boolean isEmpty() {
    return utxosDetected.isEmpty() && utxosUpdated.isEmpty() && utxosRemoved.isEmpty();
  }

  public boolean isFirstFetch() {
    return isFirstFetch;
  }

  public List<WhirlpoolUtxo> getUtxosDetected() {
    return utxosDetected;
  }

  public List<WhirlpoolUtxo> getUtxosUpdated() {
    return utxosUpdated;
  }

  public List<WhirlpoolUtxo> getUtxosRemoved() {
    return utxosRemoved;
  }
}
