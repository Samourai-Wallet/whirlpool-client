package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

public abstract class MixOrchestratorData {
  private ConcurrentHashMap<String, Mixing> mixing;
  private Set<String> mixingHashs;
  private Map<String, Integer> mixingPerPool;

  private MixingStateEditable mixingState;

  public abstract Stream<WhirlpoolUtxo> getQueue();

  public abstract Collection<Pool> getPools() throws Exception;

  public MixOrchestratorData(MixingStateEditable mixingState) {
    this.mixing = new ConcurrentHashMap<String, Mixing>();
    this.mixingHashs = new HashSet<String>();
    this.mixingPerPool = new HashMap<String, Integer>();
    this.mixingState = mixingState;
  }

  public void clear() {
    mixing.clear();
    mixingHashs.clear();
    mixingPerPool.clear();
    this.mixingState.setUtxosMixing(computeUtxosMixing());
  }

  public synchronized void removeMixing(WhirlpoolUtxo whirlpoolUtxo) {
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.remove(key);
    mixingHashs.remove(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
    mixingState.setUtxosMixing(computeUtxosMixing());
  }

  public synchronized void addMixing(Mixing mixingToAdd) {
    WhirlpoolUtxo whirlpoolUtxo = mixingToAdd.getUtxo();
    String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());
    mixing.put(key, mixingToAdd);
    mixingHashs.add(whirlpoolUtxo.getUtxo().tx_hash);
    mixingPerPool = computeMixingPerPool();
    mixingState.set(
        computeUtxosMixing(),
        getQueue().collect(Collectors.<WhirlpoolUtxo>toList())); // recount nbQueued too
  }

  private Collection<WhirlpoolUtxo> computeUtxosMixing() {
    return StreamSupport.stream(mixing.values())
        .map(
            new Function<Mixing, WhirlpoolUtxo>() {
              @Override
              public WhirlpoolUtxo apply(Mixing m) {
                return m.getUtxo();
              }
            })
        .collect(Collectors.<WhirlpoolUtxo>toList());
  }

  private Map<String, Integer> computeMixingPerPool() {
    Map<String, Integer> mixingPerPool = new HashMap<String, Integer>();
    for (Mixing mixingItem : mixing.values()) {
      String poolId = mixingItem.getPoolId();
      int currentCount = mixingPerPool.containsKey(poolId) ? mixingPerPool.get(poolId) : 0;
      mixingPerPool.put(poolId, currentCount + 1);
    }
    return mixingPerPool;
  }

  public Collection<Mixing> getMixing() {
    return mixing.values();
  }

  public Mixing getMixing(UnspentResponse.UnspentOutput utxo) {
    final String key = ClientUtils.utxoToKey(utxo);
    return mixing.get(key);
  }

  public boolean isHashMixing(String txid) {
    return mixingHashs.contains(txid);
  }

  public int getNbMixing(String poolId) {
    Integer nbMixingInPool = mixingPerPool.get(poolId);
    return (nbMixingInPool != null ? nbMixingInPool : 0);
  }

  public MixingStateEditable getMixingState() {
    return mixingState;
  }

  public void recountQueued() {
    mixingState.setUtxosQueued(getQueue().collect(Collectors.<WhirlpoolUtxo>toList()));
  }
}
