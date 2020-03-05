package com.samourai.whirlpool.client.wallet.beans;

import java.util.Comparator;
import java8.lang.Longs;

public class WhirlpoolUtxoPriorityComparator implements Comparator<WhirlpoolUtxo> {
  private static final WhirlpoolUtxoPriorityComparator instance =
      new WhirlpoolUtxoPriorityComparator();

  public static WhirlpoolUtxoPriorityComparator getInstance() {
    return instance;
  }

  protected WhirlpoolUtxoPriorityComparator() {}

  @Override
  public int compare(WhirlpoolUtxo o1, WhirlpoolUtxo o2) {
    // premix before postmix
    if (WhirlpoolAccount.PREMIX.equals(o1.getAccount())
        && WhirlpoolAccount.POSTMIX.equals(o2.getAccount())) {
      return -1;
    }
    if (WhirlpoolAccount.POSTMIX.equals(o1.getAccount())
        && WhirlpoolAccount.PREMIX.equals(o2.getAccount())) {
      return 1;
    }

    // when same priority: no error first
    WhirlpoolUtxoState s1 = o1.getUtxoState();
    WhirlpoolUtxoState s2 = o2.getUtxoState();
    if (s1.getLastError() != null && s2.getLastError() == null) {
      return 1;
    }
    if (s2.getLastError() != null && s1.getLastError() == null) {
      return -1;
    }
    if (s1.getLastError() != null && s2.getLastError() != null) {
      // both errors: older error first
      return Longs.compare(s1.getLastError(), s2.getLastError());
    }

    // same priority
    return 0;
  }
}
