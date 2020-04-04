package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.WhirlpoolClient;
import io.reactivex.Observable;

public class Mixing {
  private WhirlpoolUtxo utxo;
  private String poolId;
  private WhirlpoolClient whirlpoolClient;
  private Observable<MixProgress> observable;
  private long since;

  public Mixing(
      WhirlpoolUtxo utxo,
      String poolId,
      WhirlpoolClient whirlpoolClient,
      Observable<MixProgress> observable) {
    this.utxo = utxo;
    this.poolId = poolId;
    this.whirlpoolClient = whirlpoolClient;
    this.observable = observable;
    this.since = System.currentTimeMillis();
  }

  public WhirlpoolUtxo getUtxo() {
    return utxo;
  }

  public String getPoolId() {
    return poolId;
  }

  public WhirlpoolClient getWhirlpoolClient() {
    return whirlpoolClient;
  }

  public Observable<MixProgress> getObservable() {
    return observable;
  }

  public long getSince() {
    return since;
  }

  @Override
  public String toString() {
    return "poolId=" + poolId + ", utxo=[" + utxo + "]";
  }
}
