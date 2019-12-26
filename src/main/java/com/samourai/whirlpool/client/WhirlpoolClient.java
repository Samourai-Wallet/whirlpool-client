package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;

/** Whirlpool client */
public interface WhirlpoolClient {
  /**
   * Connect to a pool and mix.
   *
   * @param mixParams mix parameters
   * @param listener listener to get notified of mix progress
   */
  void whirlpool(MixParams mixParams, WhirlpoolClientListener listener);

  /** Abort mix. */
  void stop(boolean cancel);
}
