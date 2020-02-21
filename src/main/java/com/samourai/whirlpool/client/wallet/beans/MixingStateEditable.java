package com.samourai.whirlpool.client.wallet.beans;

import java.util.Collection;

public class MixingStateEditable extends MixingState {

  public MixingStateEditable(boolean started) {
    super(started);
  }

  @Override
  public void setStarted(boolean started) {
    super.setStarted(started);
  }

  @Override
  public synchronized void set(
      Collection<WhirlpoolUtxo> utxosMixing, Collection<WhirlpoolUtxo> utxosQueued) {
    super.set(utxosMixing, utxosQueued);
  }

  @Override
  public void setUtxosMixing(Collection<WhirlpoolUtxo> utxosMixing) {
    super.setUtxosMixing(utxosMixing);
  }

  @Override
  public synchronized void setUtxosQueued(Collection<WhirlpoolUtxo> utxosQueued) {
    super.setUtxosQueued(utxosQueued);
  }

  public synchronized void incrementUtxoQueued(WhirlpoolUtxo utxoQueued) {
    super.incrementUtxoQueued(utxoQueued);
  }
}
