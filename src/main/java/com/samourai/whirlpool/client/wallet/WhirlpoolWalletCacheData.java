package com.samourai.whirlpool.client.wallet;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.samourai.api.client.SamouraiFee;
import com.samourai.api.client.SamouraiFeeTarget;
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
import com.zeroleak.throwingsupplier.LastValueFallbackSupplier;
import com.zeroleak.throwingsupplier.Throwing;
import com.zeroleak.throwingsupplier.ThrowingSupplier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
  private Supplier<Throwing<SamouraiFee, Exception>> samouraiFee;

  // pools
  private Supplier<Throwing<Pools, Exception>> poolsResponse;
  private Supplier<Throwing<Collection<Pool>, Exception>> pools;

  // utxos
  private Map<WhirlpoolAccount, Supplier<Throwing<Map<String, WhirlpoolUtxo>, Exception>>> utxos;
  private Map<WhirlpoolAccount, Map<String, WhirlpoolUtxo>> previousUtxos;

  public WhirlpoolWalletCacheData(
      WhirlpoolWallet whirlpoolWallet,
      WhirlpoolWalletConfig config,
      WhirlpoolClient whirlpoolClient) {
    this.whirlpoolWallet = whirlpoolWallet;
    this.config = config;
    this.whirlpoolClient = whirlpoolClient;

    // fee
    this.samouraiFee =
        Suppliers.memoizeWithExpiration(initFeeSatPerByte(), FEE_REFRESH_DELAY, TimeUnit.SECONDS);

    // pools
    clearPools();

    // utxos
    this.utxos =
        new ConcurrentHashMap<
            WhirlpoolAccount, Supplier<Throwing<Map<String, WhirlpoolUtxo>, Exception>>>();
    this.previousUtxos = new ConcurrentHashMap<WhirlpoolAccount, Map<String, WhirlpoolUtxo>>();
    for (WhirlpoolAccount whirlpoolAccount : WhirlpoolAccount.values()) {
      clearUtxos(whirlpoolAccount);
    }
  }

  // FEES
  public int getFeeSatPerByte(SamouraiFeeTarget feeTarget) {
    int fee;
    try {
      fee = samouraiFee.get().getOrThrow().get(feeTarget);
    } catch (Exception e) {
      log.error("Could not fetch fee/b => fallback to " + config.getFeeFallback());
      fee = config.getFeeFallback();
    }

    // check min
    if (fee < config.getFeeMin()) {
      log.error("Fee/b too low (" + feeTarget + "): " + fee + " => " + config.getFeeMin());
      fee = config.getFeeMin();
    }

    // check max
    if (fee > config.getFeeMax()) {
      log.error("Fee/b too high (" + feeTarget + "): " + fee + " => " + config.getFeeMax());
      fee = config.getFeeMax();
    }
    return fee;
  }

  private ThrowingSupplier<SamouraiFee, Exception> initFeeSatPerByte() {
    return new LastValueFallbackSupplier<SamouraiFee, Exception>() {
      @Override
      public SamouraiFee getOrThrow() throws Exception {
        if (log.isDebugEnabled()) {
          log.debug("fetching samouraiFee");
        }
        return config.getSamouraiApi().fetchFees();
      }
    };
  }

  // POOLS

  public void clearPools() {
    this.poolsResponse =
        Suppliers.memoizeWithExpiration(initPoolsResponse(), POOLS_REFRESH_DELAY, TimeUnit.SECONDS);

    this.pools =
        Suppliers.memoizeWithExpiration(initPools(), POOLS_REFRESH_DELAY, TimeUnit.SECONDS);
  }

  public Pools getPoolsResponse() throws Exception {
    return poolsResponse.get().getOrThrow();
  }

  private ThrowingSupplier<Pools, Exception> initPoolsResponse() {
    return new LastValueFallbackSupplier<Pools, Exception>() {
      @Override
      public Pools getOrThrow() throws Exception {
        if (log.isDebugEnabled()) {
          log.debug("fetching poolsResponse");
        }
        return whirlpoolClient.fetchPools();
      }
    };
  }

  public Collection<Pool> getPools() throws Exception {
    return pools.get().getOrThrow();
  }

  private ThrowingSupplier<Collection<Pool>, Exception> initPools() {
    return new LastValueFallbackSupplier<Collection<Pool>, Exception>() {
      @Override
      public Collection<Pool> getOrThrow() throws Exception {
        if (log.isDebugEnabled()) {
          log.debug("fetching pools");
        }

        Pools pools = getPoolsResponse();

        // add pools by preference
        Collection<Pool> poolsByPreference = new LinkedList<Pool>();
        // biggest balanceMin first
        poolsByPreference =
            StreamSupport.stream(pools.getPools())
                .sorted(new WhirlpoolPoolByBalanceMinDescComparator())
                .collect(Collectors.<Pool>toList());
        return poolsByPreference;
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

  public Collection<WhirlpoolUtxo> getUtxos(boolean clearCache, WhirlpoolAccount... accounts)
      throws Exception {
    for (WhirlpoolAccount account : accounts) {
      if (clearCache) {
        clearUtxos(account);
      }
    }
    return findUtxos(accounts);
  }

  public WhirlpoolUtxo findUtxo(
      String utxoHash, int utxoIndex, WhirlpoolAccount... whirlpoolAccounts) throws Exception {
    String utxoKey = ClientUtils.utxoToKey(utxoHash, utxoIndex);
    for (WhirlpoolAccount whirlpoolAccount : whirlpoolAccounts) {
      WhirlpoolUtxo whirlpoolUtxo = utxos.get(whirlpoolAccount).get().getOrThrow().get(utxoKey);
      if (whirlpoolUtxo != null) {
        return whirlpoolUtxo;
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("findUtxo(" + utxoKey + "): not found");
    }
    return null;
  }

  private ThrowingSupplier<Map<String, WhirlpoolUtxo>, Exception> initUtxos(
      final WhirlpoolAccount whirlpoolAccount) {
    return new LastValueFallbackSupplier<Map<String, WhirlpoolUtxo>, Exception>() {
      @Override
      public Map<String, WhirlpoolUtxo> getOrThrow() throws Exception {
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
            // ClientUtils.logUtxos(fetchedUtxos);
          }
          final Map<String, UnspentOutput> freshUtxos =
              new ConcurrentHashMap<String, UnspentOutput>();
          for (UnspentOutput utxo : fetchedUtxos) {
            freshUtxos.put(utxo.toKey(), utxo);
          }

          // replace utxos
          boolean isFirstFetch = false;
          if (previousUtxos.get(whirlpoolAccount) == null) {
            previousUtxos.put(whirlpoolAccount, new ConcurrentHashMap<String, WhirlpoolUtxo>());
            isFirstFetch = true;
          }
          Map<String, WhirlpoolUtxo> oldUtxos = previousUtxos.get(whirlpoolAccount);
          Map<String, WhirlpoolUtxo> result =
              replaceUtxos(whirlpoolAccount, oldUtxos, freshUtxos, isFirstFetch);

          previousUtxos.get(whirlpoolAccount).clear();
          previousUtxos.get(whirlpoolAccount).putAll(result);
          return result;
        } catch (Exception e) {
          // exception
          log.error("Failed to fetch utxos for " + whirlpoolAccount, e);
          return new ConcurrentHashMap();
        }
      }
    };
  }

  private Collection<WhirlpoolUtxo> findUtxos(final WhirlpoolAccount... whirlpoolAccounts)
      throws Exception {
    List<WhirlpoolUtxo> result = new ArrayList<WhirlpoolUtxo>();
    for (WhirlpoolAccount whirlpoolAccount : whirlpoolAccounts) {
      Collection<WhirlpoolUtxo> accountUtxos =
          utxos.get(whirlpoolAccount).get().getOrThrow().values();
      result.addAll(accountUtxos);
    }
    return result;
  }

  private Map<String, WhirlpoolUtxo> replaceUtxos(
      final WhirlpoolAccount account,
      final Map<String, WhirlpoolUtxo> currentUtxos,
      final Map<String, UnspentOutput> freshUtxos,
      final boolean isFirstFetch) {
    final Map<String, WhirlpoolUtxo> result = new ConcurrentHashMap<String, WhirlpoolUtxo>();

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
                      new WhirlpoolUtxo(utxo, account, WhirlpoolUtxoStatus.READY, whirlpoolWallet);
                  if (!isFirstFetch) {
                    // set lastActivity when utxo is detected but ignore on first fetch
                    whirlpoolUtxo.setLastActivity();
                  }
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
