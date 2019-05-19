package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.wallet.api.backend.SamouraiFeeTarget;

public enum Tx0FeeTarget {
  BLOCKS_2(SamouraiFeeTarget.BLOCKS_2),
  BLOCKS_4(SamouraiFeeTarget.BLOCKS_4),
  BLOCKS_6(SamouraiFeeTarget.BLOCKS_6),
  BLOCKS_12(SamouraiFeeTarget.BLOCKS_12),
  BLOCKS_24(SamouraiFeeTarget.BLOCKS_24);

  private SamouraiFeeTarget feeTarget;

  public static final Tx0FeeTarget DEFAULT = Tx0FeeTarget.BLOCKS_4;

  Tx0FeeTarget(SamouraiFeeTarget feeTarget) {
    this.feeTarget = feeTarget;
  }

  public SamouraiFeeTarget getFeeTarget() {
    return feeTarget;
  }
}
