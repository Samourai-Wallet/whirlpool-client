package com.samourai.whirlpool.client.wallet.persist;

import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;

public class WhirlpoolUtxoConfigPersisted {
  private String poolId;
  private int mixsTarget;
  private int mixsDone;

  public WhirlpoolUtxoConfigPersisted() {
    this(null, 0, 0);
  }

  public WhirlpoolUtxoConfigPersisted(WhirlpoolUtxoConfig utxoConfig) {
    this.poolId = utxoConfig.getPoolId();
    this.mixsTarget = utxoConfig.getMixsTarget();
    this.mixsDone = utxoConfig.getMixsDone();
  }

  public WhirlpoolUtxoConfigPersisted(String poolId, int mixsTarget, int mixsDone) {
    this.poolId = poolId;
    this.mixsTarget = mixsTarget;
    this.mixsDone = mixsDone;
  }

  public WhirlpoolUtxoConfig toUtxoConfig(WhirlpoolWallet whirlpoolWallet) {
    return new WhirlpoolUtxoConfig(whirlpoolWallet, poolId, mixsTarget, mixsDone, 0);
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
}
