package com.samourai.whirlpool.client.tx0;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.HashMap;
import java.util.Map;

public class Tx0ParamSimple {
  private int feeTx0;
  private int feePremix;
  private Map<String, Long> overspend; // per poolId

  public Tx0ParamSimple(int feeTx0, int feePremix) {
    this.feeTx0 = feeTx0;
    this.feePremix = feePremix;
    this.overspend = new HashMap<String, Long>();
  }

  public Tx0ParamSimple(Tx0ParamSimple copy) {
    this(copy.feeTx0, copy.feePremix);
  }

  public Tx0Param computeTx0Param(Pool pool) {
    Long overspendOrNull = overspend.get(pool.getPoolId());
    return new Tx0Param(feeTx0, feePremix, pool, overspendOrNull);
  }

  public int getFeeTx0() {
    return feeTx0;
  }

  public int getFeePremix() {
    return feePremix;
  }

  public Long getOverspend(String poolId) {
    return overspend.get(poolId);
  }

  public void setOverspend(String poolId, Long overspendPerPool) {
    this.overspend.put(poolId, overspendPerPool);
  }

  @Override
  public String toString() {
    return "feeTx0=" + feeTx0 + ", feePremix=" + feePremix;
  }
}
