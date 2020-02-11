package com.samourai.whirlpool.client.tx0;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;

public class Tx0Param extends Tx0ParamSimple {
  private Pool pool;
  private long premixValue;

  public Tx0Param(int feeTx0, int feePremix, Pool pool, long premixValue) {
    super(feeTx0, feePremix);
    this.pool = pool;
    this.premixValue = premixValue;
  }

  public Tx0Param(Tx0ParamSimple copy, Pool pool, long premixValue) {
    this(copy.getFeeTx0(), copy.getFeePremix(), pool, premixValue);
  }

  public Pool getPool() {
    return pool;
  }

  public long getPremixValue() {
    return premixValue;
  }

  @Override
  public String toString() {
    return super.toString() + "pool=" + pool.getPoolId() + ", premixValue=" + premixValue;
  }
}
