package com.samourai.whirlpool.client.wallet.beans;

import java.util.Comparator;

public class WhirlpoolUtxoPriorityComparator implements Comparator<WhirlpoolUtxo> {
  @Override
  public int compare(WhirlpoolUtxo o1, WhirlpoolUtxo o2) {
    // reversed sort: highest priority first
    return o2.getPriority() - o1.getPriority();
  }
}
