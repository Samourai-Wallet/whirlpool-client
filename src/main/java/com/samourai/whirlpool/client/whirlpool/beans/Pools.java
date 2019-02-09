package com.samourai.whirlpool.client.whirlpool.beans;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java8.lang.Longs;

public class Pools {
  private List<Pool> pools;
  private String feePaymentCode;
  private byte[] feePayload;

  public Pools(Collection<Pool> pools, String feePaymentCode, byte[] feePayload) {
    this.pools = new ArrayList<Pool>(pools);

    // reversed sort: pools ordered by denomination DESC
    Collections.sort(
        this.pools,
        new Comparator<Pool>() {
          @Override
          public int compare(Pool o1, Pool o2) {
            return Longs.compare(o2.getDenomination(), o1.getDenomination());
          }
        });

    this.feePaymentCode = feePaymentCode;
    this.feePayload = feePayload;
  }

  public Pool findPoolById(String poolId) {
    for (Pool pool : pools) {
      if (pool.getPoolId().equals(poolId)) {
        return pool;
      }
    }
    return null;
  }

  public Collection<Pool> findForPremix(WhirlpoolUtxo whirlpoolUtxo) {
    long utxoValue = whirlpoolUtxo.getUtxo().value;

    List<Pool> poolsAccepted = new ArrayList<Pool>();
    // pools ordered by denomination DESC
    for (Pool pool : pools) {
      long balanceMin =
          WhirlpoolProtocol.computeInputBalanceMin(
              pool.getDenomination(), false, pool.getMinerFeeMin());
      long balanceMax =
          WhirlpoolProtocol.computeInputBalanceMax(
              pool.getDenomination(), false, pool.getMinerFeeMax());
      if (utxoValue >= balanceMin && utxoValue <= balanceMax) {
        poolsAccepted.add(pool);
      }
    }
    return poolsAccepted;
  }

  public Collection<Pool> getPools() {
    return pools;
  }

  public String getFeePaymentCode() {
    return feePaymentCode;
  }

  public byte[] getFeePayload() {
    return feePayload;
  }
}
