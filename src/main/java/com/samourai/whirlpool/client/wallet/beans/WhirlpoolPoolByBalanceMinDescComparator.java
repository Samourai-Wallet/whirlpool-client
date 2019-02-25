package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Comparator;
import java8.lang.Longs;

public class WhirlpoolPoolByBalanceMinDescComparator implements Comparator<Pool> {
  @Override
  public int compare(Pool o1, Pool o2) {
    long premixBalanceMin1 = o1.computePremixBalanceMin(false);
    long premixBalanceMin2 = o2.computePremixBalanceMin(false);

    // reversed: biggest balanceMin first
    return Longs.compare(premixBalanceMin2, premixBalanceMin1);
  }
}
