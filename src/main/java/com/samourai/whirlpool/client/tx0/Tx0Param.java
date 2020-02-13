package com.samourai.whirlpool.client.tx0;

import com.samourai.wallet.util.FeeUtil;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tx0Param extends Tx0ParamSimple {
  private static final Logger log = LoggerFactory.getLogger(Tx0Param.class);
  private static final FeeUtil feeUtil = FeeUtil.getInstance();

  private Pool pool;
  private long premixValue;

  public Tx0Param(int feeTx0, int feePremix, Pool pool, Long overspendValueOrNull) {
    super(feeTx0, feePremix);
    this.pool = pool;
    this.premixValue = computePremixValue(pool, feePremix, overspendValueOrNull);
  }

  public Tx0Param(Tx0ParamSimple copy, Pool pool, long premixValue) {
    this(copy.getFeeTx0(), copy.getFeePremix(), pool, premixValue);
  }

  private long computePremixValue(Pool pool, int feePremix, final Long overspendValueOrNull) {
    long premixOverspend;
    if (overspendValueOrNull != null && overspendValueOrNull > 0) {
      premixOverspend = overspendValueOrNull;
    } else {
      // compute premixOverspend
      long mixFeesEstimate =
          feeUtil.estimatedFeeSegwit(
              0, 0, pool.getMixAnonymitySet(), pool.getMixAnonymitySet(), 0, feePremix);
      premixOverspend = mixFeesEstimate / pool.getMinMustMix();
      if (log.isDebugEnabled()) {
        log.debug(
            "mixFeesEstimate="
                + mixFeesEstimate
                + " => premixOverspend="
                + overspendValueOrNull
                + " for poolId="
                + pool.getPoolId());
      }
    }
    long premixValue = pool.getDenomination() + premixOverspend;

    // make sure destinationValue is acceptable for pool
    long premixBalanceMin = pool.computePremixBalanceMin(false);
    long premixBalanceCap = pool.computePremixBalanceCap(false);
    long premixBalanceMax = pool.computePremixBalanceMax(false);

    long premixValueFinal = premixValue;
    premixValueFinal = Math.min(premixValueFinal, premixBalanceMax);
    premixValueFinal = Math.min(premixValueFinal, premixBalanceCap);
    premixValueFinal = Math.max(premixValueFinal, premixBalanceMin);

    if (log.isDebugEnabled()) {
      log.debug(
          "premixValueFinal="
              + premixValueFinal
              + ", premixValue="
              + premixValue
              + ", premixOverspend="
              + premixOverspend
              + " for poolId="
              + pool.getPoolId());
    }
    return premixValueFinal;
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
