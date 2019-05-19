package com.samourai.whirlpool.client.whirlpool.beans;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java8.lang.Longs;

public class Pools {
  private List<Pool> pools;

  public Pools(Collection<Pool> pools) {
    this.pools = new ArrayList<Pool>(pools);

    // reversed sort: pools ordered by denomination DESC
    Collections.sort(
        this.pools,
        new Comparator<Pool>() {
          @Override
          public int compare(Pool o1, Pool o2) {
            return Longs.compare(o2.getDenomination(), o1.getDenomination());
          }
        });
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
}
