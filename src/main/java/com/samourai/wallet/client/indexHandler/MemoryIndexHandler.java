package com.samourai.wallet.client.indexHandler;

public class MemoryIndexHandler extends AbstractIndexHandler {
  private int index;

  public MemoryIndexHandler() {
    this(IIndexHandler.DEFAULT_VALUE);
  }

  public MemoryIndexHandler(int defaultValue) {
    super();
    index = defaultValue;
  }

  @Override
  public synchronized int get() {
    return index;
  }

  @Override
  public synchronized int getAndIncrement() {
    int result = index;
    index++;
    return result;
  }

  @Override
  public synchronized void set(int value) {
    index = value;
  }
}
