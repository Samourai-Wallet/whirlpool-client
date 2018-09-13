package com.samourai.whirlpool.client.whirlpool.beans;

import java.util.Collection;

public class Pools {
    private Collection<Pool> pools;

    public Pools() {
    }

    public Pool findPoolById(String poolId) {
        for (Pool pool : pools) {
            if (pool.getPoolId().equals(poolId)) {
                return pool;
            }
        }
        return null;
    }

    public Collection<Pool> getPools() {
        return pools;
    }

    public void setPools(Collection<Pool> pools) {
        this.pools = pools;
    }
}
