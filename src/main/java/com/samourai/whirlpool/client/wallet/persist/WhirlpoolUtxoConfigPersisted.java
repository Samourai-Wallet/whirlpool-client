package com.samourai.whirlpool.client.wallet.persist;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;

public class WhirlpoolUtxoConfigPersisted {
  private String poolId;
  private int mixsTarget;
  private int mixsDone;
  private long lastSeen;

  public WhirlpoolUtxoConfigPersisted(WhirlpoolUtxoConfig utxoConfig) {
    this.poolId = utxoConfig.getPoolId();
    this.mixsTarget = utxoConfig.getMixsTarget();
    this.mixsDone = utxoConfig.getMixsDone();
    this.lastSeen = utxoConfig.getLastSeen();
  }

  public WhirlpoolUtxoConfigPersisted(String poolId, int mixsTarget, int mixsDone, long lastSeen) {
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.mixsDone = mixsDone;
    this.lastSeen = lastSeen;
  }

  public WhirlpoolUtxoConfig toUtxoConfig() {
    return new WhirlpoolUtxoConfig(poolId, mixsTarget, mixsDone, 0, lastSeen);
  }

  public String getPoolId() {
    return poolId;
  }

  public int getMixsTarget() {
    return mixsTarget;
  }

  public int getMixsDone() {
    return mixsDone;
  }

  public long getLastSeen() {
    return lastSeen;
  }
}
