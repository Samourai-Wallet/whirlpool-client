package com.samourai.whirlpool.client.whirlpool.beans;

import java.util.Collection;

public class Pools {
  private Collection<Pool> pools;
  private String feePaymentCode;

  public Pools(Collection<Pool> pools, String feePaymentCode) {
    this.pools = pools;
    this.feePaymentCode = feePaymentCode;
  }

  public Pool findPoolById(String poolId) {
    for (Pool pool : pools) {
      if (pool.getPoolId().equals(poolId)) {
        return pool;
      }
    }
    return null;
  }

  public Collection<Pool> getPools() {
    return pools;
  }

  public String getFeePaymentCode() {
    return feePaymentCode;
  }
}
