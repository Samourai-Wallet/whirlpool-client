package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Comparator;
import java8.lang.Longs;

public class WhirlpoolPoolByBalanceMinDescComparator implements Comparator<Pool> {
  @Override
  public int compare(Pool o1, Pool o2) {
    long balanceMin1 = o1.computeInputBalanceMin(false);
    long balanceMin2 = o2.computeInputBalanceMax(false);

    // reversed: biggest balanceMin first
    return Longs.compare(balanceMin2, balanceMin1);
  }
}
