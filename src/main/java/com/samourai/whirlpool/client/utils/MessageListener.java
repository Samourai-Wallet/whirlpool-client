package com.samourai.whirlpool.client.utils;

public interface MessageListener<S> {
  void onMessage(S message);
}
