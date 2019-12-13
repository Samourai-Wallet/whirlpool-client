package com.samourai.whirlpool.client.wallet.persist;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;

public class WhirlpoolUtxoConfigPersisted {
  private String poolId;
  private Integer mixsTarget;
  private int mixsDone;

  public WhirlpoolUtxoConfigPersisted() {
    this(null, null, 0);
  }

  public WhirlpoolUtxoConfigPersisted(WhirlpoolUtxoConfig utxoConfig) {
    this.poolId = utxoConfig.getPoolId();
    this.mixsTarget = utxoConfig.getMixsTarget();
    this.mixsDone = utxoConfig.getMixsDone();
  }

  public WhirlpoolUtxoConfigPersisted(String poolId, Integer mixsTarget, int mixsDone) {
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.mixsDone = mixsDone;
  }

  public WhirlpoolUtxoConfig toUtxoConfig() {
    return new WhirlpoolUtxoConfig(poolId, mixsTarget, mixsDone, 0);
  }

  public String getPoolId() {
    return poolId;
  }

  public Integer getMixsTarget() {
    return mixsTarget;
  }

  public int getMixsDone() {
    return mixsDone;
  }
}
