package com.samourai.whirlpool.client.utils;

public interface MessageErrorListener<S, E> extends MessageListener<S> {
  void onError(E error);
}
