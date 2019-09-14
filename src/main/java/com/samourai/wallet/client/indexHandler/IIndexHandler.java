package com.samourai.wallet.client.indexHandler;

public interface IIndexHandler {
  int DEFAULT_VALUE = 0;

  int getAndIncrement();

  int get();

  void set(int value);

  int getUnconfirmed();

  int getAndIncrementUnconfirmed();

  void confirmUnconfirmed(int index);

  void cancelUnconfirmed(int index);
}
