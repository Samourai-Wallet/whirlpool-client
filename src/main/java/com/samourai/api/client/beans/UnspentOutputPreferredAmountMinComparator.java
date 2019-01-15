package com.samourai.api.client.beans;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import java.util.Comparator;

public class UnspentOutputPreferredAmountMinComparator implements Comparator<UnspentOutput> {
  private long preferredAmountMin;

  public UnspentOutputPreferredAmountMinComparator(long preferredAmountMin) {
    this.preferredAmountMin = preferredAmountMin;
  }

  @Override
  public int compare(UnspentOutput o1, UnspentOutput o2) {
    // prioritize UTXOs >= preferredAmountMin
    if (o1.value >= preferredAmountMin && o2.value < preferredAmountMin) {
      return 1;
    }
    if (o2.value >= preferredAmountMin && o1.value < preferredAmountMin) {
      return -1;
    }

    if (o1.value >= preferredAmountMin && o2.value >= preferredAmountMin) {
      // both are >, take lowest (closest to preferredAmountMin)
      return o1.value - o2.value > 0 ? 1 : -1;
    } else {
      // both are <, take highest (closest to preferredAmountMin)
      return o1.value - o2.value > 0 ? -1 : 1;
    }
  }
}
