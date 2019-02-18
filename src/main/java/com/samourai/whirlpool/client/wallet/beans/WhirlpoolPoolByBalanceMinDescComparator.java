package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.util.Comparator;
import java8.lang.Longs;

public class WhirlpoolPoolByBalanceMinDescComparator implements Comparator<Pool> {
  @Override
  public int compare(Pool o1, Pool o2) {
    long balanceMin1 =
        WhirlpoolProtocol.computeInputBalanceMin(o1.getDenomination(), false, o1.getMinerFeeMin());
    long balanceMin2 =
        WhirlpoolProtocol.computeInputBalanceMin(o2.getDenomination(), false, o2.getMinerFeeMin());

    // reversed: biggest balanceMin first
    return Longs.compare(balanceMin2, balanceMin1);
  }
}
