package com.samourai.whirlpool.client.wallet.beans;

import java.util.Comparator;
import java8.lang.Longs;

public class WhirlpoolUtxoPriorityComparator implements Comparator<WhirlpoolUtxo> {
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

    // when same priority: last activity first
    // if (o1.getUtxoConfig().getPriority() == o2.getUtxoConfig().getPriority()) {
    if (o1.getLastActivity() != null && o2.getLastActivity() == null) {
      return -1;
    }
    if (o2.getLastActivity() != null && o1.getLastActivity() == null) {
      return 1;
    }
    if (o1.getLastActivity() == null && o2.getLastActivity() == null) {
      return 0;
    }
    return Longs.compare(o2.getLastActivity(), o1.getLastActivity());
    // }

    // reversed sort: highest priority first
    // return o2.getUtxoConfig().getPriority() - o1.getUtxoConfig().getPriority();
  }
}
