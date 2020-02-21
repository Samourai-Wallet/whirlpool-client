package com.samourai.whirlpool.client.wallet.beans;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import java.util.ArrayList;
import java.util.Collection;
import java8.util.function.Predicate;
import java8.util.stream.StreamSupport;

public class MixingState {
  private boolean started;
  private Collection<WhirlpoolUtxo> utxosMixing;
  private int nbMixing;
  private int nbMixingMustMix;
  private int nbMixingLiquidity;
  private int nbQueued;
  private int nbQueuedMustMix;
  private int nbQueuedLiquidity;
  private Subject<MixingState> observable;

  public MixingState(boolean started) {
    this.started = started;
    doSetUtxosMixing(new ArrayList<WhirlpoolUtxo>());
    doSetUtxosQueued(new ArrayList<WhirlpoolUtxo>());
    this.observable = BehaviorSubject.create();
  }

  protected void setStarted(boolean started) {
    this.started = started;
    emit();
  }

  public boolean isStarted() {
    return started;
  }

  private void doSetUtxosMixing(Collection<WhirlpoolUtxo> utxosMixing) {
    this.utxosMixing = utxosMixing;
    this.nbMixing = utxosMixing.size();
    this.nbMixingLiquidity =
        (int)
            StreamSupport.stream(utxosMixing)
                .filter(
                    new Predicate<WhirlpoolUtxo>() {
                      @Override
                      public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                        return WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount());
                      }
                    })
                .count();
    this.nbMixingMustMix = this.nbMixing - this.nbMixingLiquidity;
  }

  private void doSetUtxosQueued(Collection<WhirlpoolUtxo> utxosQueued) {
    this.nbQueued = utxosQueued.size();
    this.nbQueuedLiquidity =
        (int)
            StreamSupport.stream(utxosQueued)
                .filter(
                    new Predicate<WhirlpoolUtxo>() {
                      @Override
                      public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                        return WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount());
                      }
                    })
                .count();
    this.nbQueuedMustMix = this.nbQueued - this.nbQueuedLiquidity;
  }

  protected void incrementUtxoQueued(WhirlpoolUtxo utxoQueued) {
    if (WhirlpoolAccount.POSTMIX.equals(utxoQueued.getAccount())) {
      nbQueuedLiquidity++;
    } else {
      nbQueuedMustMix++;
    }
    nbQueued++;
  }

  protected synchronized void set(
      Collection<WhirlpoolUtxo> utxosMixing, Collection<WhirlpoolUtxo> utxosQueued) {
    doSetUtxosMixing(utxosMixing);
    doSetUtxosQueued(utxosQueued);
    emit();
  }

  protected synchronized void setUtxosMixing(Collection<WhirlpoolUtxo> utxosMixing) {
    doSetUtxosMixing(utxosMixing);
    emit();
  }

  protected synchronized void setUtxosQueued(Collection<WhirlpoolUtxo> utxosQueued) {
    doSetUtxosQueued(utxosQueued);
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

  public int getNbMixingMustMix() {
    return nbMixingMustMix;
  }

  public int getNbMixingLiquidity() {
    return nbMixingLiquidity;
  }

  public int getNbQueued() {
    return nbQueued;
  }

  public int getNbQueuedMustMix() {
    return nbQueuedMustMix;
  }

  public int getNbQueuedLiquidity() {
    return nbQueuedLiquidity;
  }

  public Observable<MixingState> getObservable() {
    return observable;
  }
}
