package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.whirlpool.httpClient.HttpClient;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.mix.MixParams;
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
     * @param poolId pool id to join
     * @param mixParams mix parameters (inputs, outputs)
     * @param mixs number of mix to achieve (1-N)
     * @param listener listener to get notified of mix progress (in real time)
     */
    void whirlpool(String poolId, long denomination, MixParams mixParams, int mixs, WhirlpoolClientListener listener);

    /**
     * Abort mix.
     */
    void exit();
}
