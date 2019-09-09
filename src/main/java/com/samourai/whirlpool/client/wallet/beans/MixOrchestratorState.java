package com.samourai.whirlpool.client.wallet.beans;

import java.util.Collection;

public class MixOrchestratorState {
  private Collection<WhirlpoolUtxo> utxosMixing;
  private int nbMixing;
  private int nbQueued;

  public MixOrchestratorState(Collection<WhirlpoolUtxo> utxosMixing, int nbQueued) {
    this.utxosMixing = utxosMixing;
    this.nbMixing = utxosMixing.size();
    this.nbQueued = nbQueued;
  }

  public Collection<WhirlpoolUtxo> getUtxosMixing() {
    return utxosMixing;
  }

  public int getNbMixing() {
    return nbMixing;
  }

  public int getNbQueued() {
    return nbQueued;
  }
}
