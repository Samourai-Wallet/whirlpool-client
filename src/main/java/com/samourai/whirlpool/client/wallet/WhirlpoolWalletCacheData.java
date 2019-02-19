package com.samourai.whirlpool.client.wallet;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolPoolByBalanceMinDescComparator;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java8.util.function.Consumer;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thread-safe cache data for WhirlpooWallet. */
public class WhirlpoolWalletCacheData {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWalletCacheData.class);
  private static final long FEE_REFRESH_DELAY = 300; // 5m
  private static final long POOLS_REFRESH_DELAY = 3600; // 1h

  private WhirlpoolWallet whirlpoolWallet;
  private WhirlpoolWalletConfig config;
  private WhirlpoolClient whirlpoolClient;

  // fee
  private Supplier<Integer> feeSatPerByte;

  // pools
  private Supplier<Pools> pools;
  private Supplier<Collection<Pool>> poolsByPriority;

  // utxos
  private Map<WhirlpoolAccount, Supplier<Map<String, WhirlpoolUtxo>>> utxos;
  private Map<WhirlpoolAccount, Map<String, WhirlpoolUtxo>> previousUtxos;

  public WhirlpoolWalletCacheData(
      WhirlpoolWallet whirlpoolWallet,
      WhirlpoolWalletConfig config,
      WhirlpoolClient whirlpoolClient) {
    this.whirlpoolWallet = whirlpoolWallet;
    this.config = config;
    this.whirlpoolClient = whirlpoolClient;

    // fee
    this.feeSatPerByte =
        Suppliers.memoizeWithExpiration(initFeeSatPerByte(), FEE_REFRESH_DELAY, TimeUnit.SECONDS);

    // pools
    this.pools =
        Suppliers.memoizeWithExpiration(initPools(), POOLS_REFRESH_DELAY, TimeUnit.SECONDS);
    this.poolsByPriority =
        Suppliers.memoizeWithExpiration(
            initPoolsByPriority(), POOLS_REFRESH_DELAY, TimeUnit.SECONDS);

    // utxos
    this.utxos = new HashMap<WhirlpoolAccount, Supplier<Map<String, WhirlpoolUtxo>>>();
    this.previousUtxos = new HashMap<WhirlpoolAccount, Map<String, WhirlpoolUtxo>>();
    for (WhirlpoolAccount whirlpoolAccount : WhirlpoolAccount.values()) {
      clearUtxos(whirlpoolAccount);
      this.previousUtxos.put(whirlpoolAccount, new HashMap<String, WhirlpoolUtxo>());
    }
  }

  // FEES
  public int getFeeSatPerByte() {
    return feeSatPerByte.get();
  }

  private Supplier<Integer> initFeeSatPerByte() {
    return new Supplier<Integer>() {
      @Override
      public Integer get() {
        if (log.isDebugEnabled()) {
          log.debug("fetching feeSatPerByte");
        }
        return config.getSamouraiApi().fetchFees();
      }
    };
  }

  // POOLS

  public Pools getPools() {
    return pools.get();
  }

  private Supplier<Pools> initPools() {
    return new Supplier<Pools>() {
      @Override
      public Pools get() {
        try {
          if (log.isDebugEnabled()) {
            log.debug("fetching pools");
          }
          return whirlpoolClient.fetchPools();
        } catch (Exception e) {
          log.error("Unable to fetch pools", e);
          return new Pools(new ArrayList<Pool>(), null, null);
        }
      }
    };
  }

  public Collection<Pool> getPoolsByPriority() throws Exception {
    return poolsByPriority.get();
  }

  private Supplier<Collection<Pool>> initPoolsByPriority() {
    return new Supplier<Collection<Pool>>() {
      @Override
      public Collection<Pool> get() {
        if (log.isDebugEnabled()) {
          log.debug("fetching poolsByPriority");
        }

        Pools pools = getPools();

        // add pools by priority
        Collection<Pool> poolsByPriority = new LinkedList<Pool>();
        if (config.getPoolIdsByPriority() != null && !config.getPoolIdsByPriority().isEmpty()) {
          // use user-specified pools
          for (String poolId : config.getPoolIdsByPriority()) {
            Pool pool = pools.findPoolById(poolId);
            if (pool != null) {
              poolsByPriority.add(pool);
            } else {
              log.error("No such pool: " + poolId);
            }
          }
        } else {
          // use all pools
          if (log.isDebugEnabled()) {
            log.debug("getPoolsByPriority: no priority defined, using all pools");
          }
          // biggest balanceMin first
          poolsByPriority =
              StreamSupport.stream(pools.getPools())
                  .sorted(new WhirlpoolPoolByBalanceMinDescComparator())
                  .collect(Collectors.<Pool>toList());
        }
        return poolsByPriority;
      }
    };
  }

  // UTXOS

  public void clearUtxos(WhirlpoolAccount whirlpoolAccount) {
    if (log.isDebugEnabled()) {
      log.debug("clearing utxos for " + whirlpoolAccount);
    }
    this.utxos.put(
        whirlpoolAccount,
        Suppliers.memoizeWithExpiration(
            initUtxos(whirlpoolAccount), config.getRefreshUtxoDelay(), TimeUnit.SECONDS));
  }

  public Collection<WhirlpoolUtxo> getUtxos(boolean clearCache, WhirlpoolAccount... accounts) {
    for (WhirlpoolAccount account : accounts) {
      if (clearCache) {
        clearUtxos(account);
      }
    }
    return findUtxos(accounts);
  }

  private Supplier<Map<String, WhirlpoolUtxo>> initUtxos(final WhirlpoolAccount whirlpoolAccount) {
    return new Supplier<Map<String, WhirlpoolUtxo>>() {
      @Override
      public Map<String, WhirlpoolUtxo> get() {
        try {
          Bip84ApiWallet wallet = whirlpoolWallet.getWallet(whirlpoolAccount);
          List<UnspentOutput> fetchedUtxos = wallet.fetchUtxos();
          if (log.isDebugEnabled()) {
            log.debug(
                "Fetching utxos from "
                    + whirlpoolAccount
                    + "... "
                    + fetchedUtxos.size()
                    + " utxos found");
            ClientUtils.logUtxos(fetchedUtxos);
          }
          final Map<String, UnspentOutput> freshUtxos = new HashMap<String, UnspentOutput>();
          for (UnspentOutput utxo : fetchedUtxos) {
            freshUtxos.put(utxo.toKey(), utxo);
          }

          // replace utxos
          Map<String, WhirlpoolUtxo> oldUtxos = previousUtxos.get(whirlpoolAccount);
          Map<String, WhirlpoolUtxo> result = replaceUtxos(whirlpoolAccount, oldUtxos, freshUtxos);

          previousUtxos.get(whirlpoolAccount).clear();
          previousUtxos.get(whirlpoolAccount).putAll(result);
          return result;
        } catch (Exception e) {
          // exception
          log.error("Failed to fetch utxos for " + whirlpoolAccount, e);
          return new HashMap();
        }
      }
    };
  }

  private Collection<WhirlpoolUtxo> findUtxos(final WhirlpoolAccount... whirlpoolAccounts) {
    List<WhirlpoolUtxo> result = new ArrayList<WhirlpoolUtxo>();
    for (WhirlpoolAccount whirlpoolAccount : whirlpoolAccounts) {
      Collection<WhirlpoolUtxo> accountUtxos = utxos.get(whirlpoolAccount).get().values();
      result.addAll(accountUtxos);
    }
    return result;
  }

  private Map<String, WhirlpoolUtxo> replaceUtxos(
      final WhirlpoolAccount account,
      final Map<String, WhirlpoolUtxo> currentUtxos,
      final Map<String, UnspentOutput> freshUtxos) {
    final Map<String, WhirlpoolUtxo> result = new HashMap<String, WhirlpoolUtxo>();

    // add existing utxos
    StreamSupport.stream(currentUtxos.values())
        .forEach(
            new Consumer<WhirlpoolUtxo>() {
              @Override
              public void accept(WhirlpoolUtxo whirlpoolUtxo) {
                String key = whirlpoolUtxo.getUtxo().toKey();

                UnspentOutput freshUtxo = freshUtxos.get(key);
                if (freshUtxo != null) {
                  UnspentOutput oldUtxo = whirlpoolUtxo.getUtxo();

                  // update existing utxo
                  whirlpoolUtxo.setUtxo(freshUtxo);
                  whirlpoolWallet.onUtxoUpdated(whirlpoolUtxo, oldUtxo);

                  // add
                  result.put(key, whirlpoolUtxo);
                } else {
                  // ignore obsolete
                  whirlpoolWallet.onUtxoRemoved(whirlpoolUtxo);
                }
              }
            });

    // add missing utxos
    StreamSupport.stream(freshUtxos.values())
        .forEach(
            new Consumer<UnspentOutput>() {
              @Override
              public void accept(UnspentOutput utxo) {
                String key = utxo.toKey();
                if (!currentUtxos.containsKey(key)) {
                  // add missing
                  WhirlpoolUtxo whirlpoolUtxo =
                      new WhirlpoolUtxo(utxo, account, WhirlpoolUtxoStatus.READY);
                  whirlpoolWallet.onUtxoDetected(whirlpoolUtxo);
                  result.put(key, whirlpoolUtxo);
                }
              }
            });
    if (log.isDebugEnabled()) {
      log.debug(
          "replaceUtxos: current="
              + currentUtxos.size()
              + ", fresh="
              + freshUtxos.size()
              + ", result="
              + result.size());
    }
    return result;
  }
}
