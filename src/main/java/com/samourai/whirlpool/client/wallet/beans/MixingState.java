package com.samourai.whirlpool.client.wallet.beans;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import java.util.Collection;

public class MixingState {
  private boolean started;
  private Collection<WhirlpoolUtxo> utxosMixing;
  private int nbMixing;
  private int nbQueued;
  private PublishSubject<MixingState> observable;

  public MixingState(boolean started, Collection<WhirlpoolUtxo> utxosMixing, int nbQueued) {
    this.started = started;
    this.utxosMixing = utxosMixing;
    this.nbMixing = utxosMixing.size();
    this.nbQueued = nbQueued;
    this.observable = PublishSubject.create();
  }

  protected void setStarted(boolean started) {
    this.started = started;
    emit();
  }

  public boolean isStarted() {
    return started;
  }

  protected synchronized void set(Collection<WhirlpoolUtxo> utxosMixing, int nbQueued) {
    this.utxosMixing = utxosMixing;
    this.nbMixing = utxosMixing.size();
    this.nbQueued = nbQueued;
    emit();
  }

  protected synchronized void setUtxosMixing(Collection<WhirlpoolUtxo> utxosMixing) {
    this.utxosMixing = utxosMixing;
    this.nbMixing = utxosMixing.size();
    emit();
  }

  protected synchronized void setNbQueued(int nbQueued) {
    this.nbQueued = nbQueued;
    emit();
  }

  protected void emit() {
    // notify observers
    observable.onNext(this);
  }

  @Override
  public String toString() {
    return nbQueued + " queued, " + nbMixing + " mixing: " + utxosMixing;
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

  public Observable<MixingState> getObservable() {
    return observable;
  }
}
