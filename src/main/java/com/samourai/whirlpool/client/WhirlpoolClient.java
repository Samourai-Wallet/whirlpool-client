package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;

/**
 * Whirlpool client
 */
public interface WhirlpoolClient {

    /**
     * Retrieve list of available pools.
     * @return
     * @throws Exception
     */
    Pools fetchPools() throws Exception;

    /**
     * Connect to a pool and mix.
     * @param mixParams mix parameters
     * @param mixs number of mix to achieve
     * @param listener listener to get notified of mix progress
     */
    void whirlpool(MixParams mixParams, int mixs, WhirlpoolClientListener listener);

    /**
     * Abort mix.
     */
    void exit();
}
