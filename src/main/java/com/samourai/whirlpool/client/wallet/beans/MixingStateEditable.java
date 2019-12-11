package com.samourai.whirlpool.client.wallet.beans;

import java.util.Collection;

public class MixingStateEditable extends MixingState {

  public MixingStateEditable(boolean started, Collection<WhirlpoolUtxo> utxosMixing, int nbQueued) {
    super(started, utxosMixing, nbQueued);
  }

  @Override
  public void setStarted(boolean started) {
    super.setStarted(started);
  }

  @Override
  public synchronized void set(Collection<WhirlpoolUtxo> utxosMixing, int nbQueued) {
    super.set(utxosMixing, nbQueued);
  }

  @Override
  public void setUtxosMixing(Collection<WhirlpoolUtxo> utxosMixing) {
    super.setUtxosMixing(utxosMixing);
  }

  @Override
  public synchronized void setNbQueued(int nbQueued) {
    super.setNbQueued(nbQueued);
  }

  public synchronized void incrementNbQueued() {
    super.setNbQueued(getNbQueued() + 1);
  }
}
