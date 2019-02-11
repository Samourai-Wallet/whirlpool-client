package com.samourai.whirlpool.client.wallet.beans;

import java.util.Collection;

public class MixOrchestratorState {
  private Collection<WhirlpoolUtxo> utxosMixing;
  private int nbMixing;
  private int maxClients;
  private int nbIdle;
  private int nbQueued;

  public MixOrchestratorState(
      Collection<WhirlpoolUtxo> utxosMixing, int maxClients, int nbIdle, int nbQueued) {
    this.utxosMixing = utxosMixing;
    this.nbMixing = utxosMixing.size();
    this.maxClients = maxClients;
    this.nbIdle = nbIdle;
    this.nbQueued = nbQueued;
  }

  public Collection<WhirlpoolUtxo> getUtxosMixing() {
    return utxosMixing;
  }

  public int getNbMixing() {
    return nbMixing;
  }

  public int getMaxClients() {
    return maxClients;
  }

  public int getNbIdle() {
    return nbIdle;
  }

  public int getNbQueued() {
    return nbQueued;
  }
}
