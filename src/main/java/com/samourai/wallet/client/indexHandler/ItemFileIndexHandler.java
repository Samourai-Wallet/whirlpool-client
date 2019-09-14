package com.samourai.wallet.client.indexHandler;

public class ItemFileIndexHandler extends AbstractIndexHandler {
  private FileIndexHandler fileIndexHandler;
  private String key;
  private int defaultValue;

  public ItemFileIndexHandler(FileIndexHandler fileIndexHandler, String key, int defaultValue) {
    super();
    this.fileIndexHandler = fileIndexHandler;
    this.key = key;
    this.defaultValue = defaultValue;
  }

  @Override
  public int get() {
    return fileIndexHandler.get(key, defaultValue);
  }

  @Override
  public synchronized int getAndIncrement() {
    return fileIndexHandler.getAndIncrement(key, defaultValue);
  }

  @Override
  public synchronized void set(int value) {
    fileIndexHandler.set(key, value);
  }
}
