package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.wallet.api.backend.MinerFeeTarget;

public enum Tx0FeeTarget {
  BLOCKS_2(MinerFeeTarget.BLOCKS_2),
  BLOCKS_4(MinerFeeTarget.BLOCKS_4),
  BLOCKS_6(MinerFeeTarget.BLOCKS_6),
  BLOCKS_12(MinerFeeTarget.BLOCKS_12),
  BLOCKS_24(MinerFeeTarget.BLOCKS_24);

  private MinerFeeTarget feeTarget;

  public static final Tx0FeeTarget MIN = Tx0FeeTarget.BLOCKS_24;

  Tx0FeeTarget(MinerFeeTarget feeTarget) {
    this.feeTarget = feeTarget;
  }

  public MinerFeeTarget getFeeTarget() {
    return feeTarget;
  }
}
