package com.samourai.api.client.beans;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import java.util.Comparator;

public class UnspentOutputComparator implements Comparator<UnspentOutput> {

  @Override
  public int compare(UnspentOutput o1, UnspentOutput o2) {
    return o1.value - o2.value > 0 ? 1 : -1;
  }
}
