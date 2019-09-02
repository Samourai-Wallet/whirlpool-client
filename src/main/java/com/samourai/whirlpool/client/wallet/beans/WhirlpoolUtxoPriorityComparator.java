package com.samourai.whirlpool.client.wallet.beans;

import com.google.common.primitives.Ints;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java8.lang.Longs;

public class WhirlpoolUtxoPriorityComparator implements Comparator<WhirlpoolUtxo> {
  private Set<String> mixingHashs;
  private Map<String, Integer> mixingPerPool;

  public WhirlpoolUtxoPriorityComparator(
      Set<String> mixingHashs, Map<String, Integer> mixingPerPool) {
    this.mixingHashs = mixingHashs;
    this.mixingPerPool = mixingPerPool;
  }

  private int getMixingPerPool(String poolId) {
    return mixingPerPool.containsKey(poolId) ? mixingPerPool.get(poolId) : 0;
  }

  @Override
  public int compare(WhirlpoolUtxo o1, WhirlpoolUtxo o2) {
    // less active pool first
    String pool1 = o1.getUtxoConfig().getPoolId();
    String pool2 = o2.getUtxoConfig().getPoolId();
    if (pool1 != null && pool2 != null) {
      int comparePools = Ints.compare(getMixingPerPool(pool1), getMixingPerPool(pool2));
      if (comparePools != 0) {
        return comparePools;
      }
    }

    // non-mixing txid first
    String hash1 = o1.getUtxo().tx_hash;
    String hash2 = o2.getUtxo().tx_hash;
    if (!mixingHashs.contains(hash1) && mixingHashs.contains(hash2)) {
      return -1;
    }
    if (mixingHashs.contains(hash1) && !mixingHashs.contains(hash2)) {
      return 1;
    }

    // premix before postmix
    if (WhirlpoolAccount.PREMIX.equals(o1.getAccount())
        && WhirlpoolAccount.POSTMIX.equals(o2.getAccount())) {
      return -1;
    }
    if (WhirlpoolAccount.POSTMIX.equals(o1.getAccount())
        && WhirlpoolAccount.PREMIX.equals(o2.getAccount())) {
      return 1;
    }

    // when same priority: no error first
    if (o1.getLastError() != null && o2.getLastError() == null) {
      return 1;
    }
    if (o2.getLastError() != null && o1.getLastError() == null) {
      return -1;
    }
    if (o1.getLastError() != null && o2.getLastError() != null) {
      // both errors: older error first
      return Longs.compare(o1.getLastError(), o2.getLastError());
    }

    // when same priority: last activity first
    if (o1.getLastActivity() != null && o2.getLastActivity() == null) {
      return -1;
    }
    if (o2.getLastActivity() != null && o1.getLastActivity() == null) {
      return 1;
    }
    if (o1.getLastActivity() == null && o2.getLastActivity() == null) {
      return 0;
    }
    return Longs.compare(o2.getLastActivity(), o1.getLastActivity());
  }
}
