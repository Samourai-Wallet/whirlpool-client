package com.samourai.wallet.client.indexHandler;

import com.google.common.primitives.Ints;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java8.util.function.Consumer;
import java8.util.stream.StreamSupport;

public abstract class AbstractIndexHandler implements IIndexHandler {
  private Set<Integer> unconfirmedIndexs;

  public AbstractIndexHandler() {
    unconfirmedIndexs = new HashSet<Integer>();
  }

  @Override
  public synchronized int getUnconfirmed() {
    int current = get();

    if (unconfirmedIndexs.isEmpty()) {
      return current;
    }
    int currentUnconfirmed = Collections.max(unconfirmedIndexs) + 1;
    return Ints.max(current, currentUnconfirmed);
  }

  @Override
  public synchronized int getAndIncrementUnconfirmed() {
    int nextUnconfirmed = getUnconfirmed();
    unconfirmedIndexs.add(nextUnconfirmed);
    return nextUnconfirmed;
  }

  @Override
  public synchronized void confirmUnconfirmed(final int confirmed) {
    if (confirmed >= get()) {
      set(confirmed + 1);
    }
    StreamSupport.stream(unconfirmedIndexs)
        .forEach(
            new Consumer<Integer>() {
              @Override
              public void accept(Integer value) {
                if (value <= confirmed) {
                  unconfirmedIndexs.remove(value);
                }
              }
            });
  }

  @Override
  public synchronized void cancelUnconfirmed(int unconfirmed) {
    unconfirmedIndexs.remove(unconfirmed);
  }
}
