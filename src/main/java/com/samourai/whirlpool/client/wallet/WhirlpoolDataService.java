package com.samourai.whirlpool.client.wallet;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.PoolInfo;
import com.samourai.whirlpool.protocol.rest.PoolsResponse;
import com.zeroleak.throwingsupplier.LastValueFallbackSupplier;
import com.zeroleak.throwingsupplier.Throwing;
import com.zeroleak.throwingsupplier.ThrowingSupplier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java8.util.Optional;
import java8.util.function.Consumer;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thread-safe cache data for WhirlpooWallet. */
public class WhirlpoolDataService {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolDataService.class);

  private WhirlpoolWalletConfig config;
  private WhirlpoolWalletService whirlpoolWalletService;

  // fee
  private Supplier<Throwing<MinerFee, Exception>> minerFee;

  // pools
  private Supplier<Throwing<Pools, Exception>> poolsResponse;
  private Supplier<Throwing<Collection<Pool>, Exception>> pools;

  // utxos
  private Map<WhirlpoolAccount, Supplier<Throwing<Map<String, WhirlpoolUtxo>, Exception>>> utxos;
  private Map<WhirlpoolAccount, Map<String, WhirlpoolUtxo>> previousUtxos;

  private static final int ATTEMPTS = 2;

  public WhirlpoolDataService(
      WhirlpoolWalletConfig config, WhirlpoolWalletService whirlpoolWalletService) {
    this.config = config;
    this.whirlpoolWalletService = whirlpoolWalletService;
    this.utxos =
        new ConcurrentHashMap<
            WhirlpoolAccount, Supplier<Throwing<Map<String, WhirlpoolUtxo>, Exception>>>();

    clear();
  }

  public void clear() {
    clearMinerFee();
    clearPools();
    clearUtxos();
  }

  // FEES
  public void clearMinerFee() {
    this.minerFee =
        Suppliers.memoizeWithExpiration(
            initMinerFee().attempts(ATTEMPTS), config.getRefreshFeeDelay(), TimeUnit.SECONDS);
  }

  public int getFeeSatPerByte(MinerFeeTarget feeTarget) {
    int fee;
    try {
      fee = minerFee.get().getOrThrow().get(feeTarget);
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

  private ThrowingSupplier<MinerFee, Exception> initMinerFee() {
    return new LastValueFallbackSupplier<MinerFee, Exception>() {
      @Override
      public MinerFee getOrThrow() throws Exception {
        if (log.isDebugEnabled()) {
          log.debug("fetching minerFee");
        }
        return fetchMinerFee();
      }
    };
  }

  protected MinerFee fetchMinerFee() throws Exception {
    return config.getBackendApi().fetchMinerFee();
  }

  // POOLS

  public void clearPools() {
    this.poolsResponse =
        Suppliers.memoizeWithExpiration(
            initPoolsResponse().attempts(ATTEMPTS),
            config.getRefreshPoolsDelay(),
            TimeUnit.SECONDS);

    this.pools =
        Suppliers.memoizeWithExpiration(
            initPools().attempts(ATTEMPTS), config.getRefreshPoolsDelay(), TimeUnit.SECONDS);
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
        return fetchPools();
      }
    };
  }

  protected Pools fetchPools() throws Exception {
    String url = WhirlpoolProtocol.getUrlFetchPools(config.getServer());
    try {
      PoolsResponse poolsResponse = config.getHttpClient().getJson(url, PoolsResponse.class, null);
      return computePools(poolsResponse);
    } catch (HttpException e) {
      String restErrorResponseMessage = ClientUtils.parseRestErrorMessage(e);
      if (restErrorResponseMessage != null) {
        throw new NotifiableException(restErrorResponseMessage);
      }
      throw e;
    }
  }

  private Pools computePools(PoolsResponse poolsResponse) {
    List<Pool> listPools = new ArrayList<Pool>();
    for (PoolInfo poolInfo : poolsResponse.pools) {
      Pool pool = new Pool();
      pool.setPoolId(poolInfo.poolId);
      pool.setDenomination(poolInfo.denomination);
      pool.setFeeValue(poolInfo.feeValue);
      pool.setMustMixBalanceMin(poolInfo.mustMixBalanceMin);
      pool.setMustMixBalanceCap(poolInfo.mustMixBalanceCap);
      pool.setMustMixBalanceMax(poolInfo.mustMixBalanceMax);
      pool.setMinAnonymitySet(poolInfo.minAnonymitySet);
      pool.setMinMustMix(poolInfo.minMustMix);
      pool.setNbRegistered(poolInfo.nbRegistered);

      pool.setMixAnonymitySet(poolInfo.mixAnonymitySet);
      pool.setMixStatus(poolInfo.mixStatus);
      pool.setElapsedTime(poolInfo.elapsedTime);
      pool.setNbConfirmed(poolInfo.nbConfirmed);
      listPools.add(pool);
    }
    Pools pools = new Pools(listPools);
    return pools;
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

  public void clearUtxos() {
    this.previousUtxos = new ConcurrentHashMap<WhirlpoolAccount, Map<String, WhirlpoolUtxo>>();
    for (WhirlpoolAccount whirlpoolAccount : WhirlpoolAccount.values()) {
      clearUtxos(whirlpoolAccount);
    }
  }

  public void clearUtxos(WhirlpoolAccount whirlpoolAccount) {
    if (log.isDebugEnabled()) {
      log.debug("clearing utxos for " + whirlpoolAccount);
    }
    this.utxos.put(
        whirlpoolAccount,
        Suppliers.memoizeWithExpiration(
            initUtxos(whirlpoolAccount).attempts(ATTEMPTS),
            config.getRefreshUtxoDelay(),
            TimeUnit.SECONDS));
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
        Optional<WhirlpoolWallet> whirlpoolWalletOpt = whirlpoolWalletService.getWhirlpoolWallet();
        if (!whirlpoolWalletOpt.isPresent()) {
          throw new Exception("no WhirlpoolWallet opened");
        }
        WhirlpoolWallet whirlpoolWallet = whirlpoolWalletOpt.get();
        try {
          List<UnspentOutput> fetchedUtxos = fetchUtxos(whirlpoolAccount, whirlpoolWallet);
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
            freshUtxos.put(ClientUtils.utxoToKey(utxo), utxo);
          }

          // replace utxos
          boolean isFirstFetch = false;
          if (previousUtxos.get(whirlpoolAccount) == null) {
            previousUtxos.put(whirlpoolAccount, new ConcurrentHashMap<String, WhirlpoolUtxo>());
            isFirstFetch = true;
          }
          Map<String, WhirlpoolUtxo> oldUtxos = previousUtxos.get(whirlpoolAccount);
          Map<String, WhirlpoolUtxo> result =
              replaceUtxos(whirlpoolAccount, whirlpoolWallet, oldUtxos, freshUtxos, isFirstFetch);

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

  protected List<UnspentOutput> fetchUtxos(
      WhirlpoolAccount whirlpoolAccount, WhirlpoolWallet whirlpoolWallet) throws Exception {
    Bip84ApiWallet wallet = whirlpoolWallet.getWallet(whirlpoolAccount);
    List<UnspentOutput> utxos = wallet.fetchUtxos();

    // refresh wallet indexs (to avoid address reuse while using mobile wallet)
    try {
      wallet.refreshIndexs();
    } catch (Exception e) {
      log.error("refreshIndexs failed", e);
    }

    return utxos;
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
      final WhirlpoolWallet whirlpoolWallet,
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
                String key = ClientUtils.utxoToKey(whirlpoolUtxo.getUtxo());

                UnspentOutput freshUtxo = freshUtxos.get(key);
                if (freshUtxo != null) {
                  UnspentOutput oldUtxo = whirlpoolUtxo.getUtxo();

                  // set existing utxo
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
                String key = ClientUtils.utxoToKey(utxo);
                if (!currentUtxos.containsKey(key)) {
                  // add missing
                  WhirlpoolUtxoConfig utxoConfig = whirlpoolWallet.computeUtxoConfig(utxo, account);
                  WhirlpoolUtxo whirlpoolUtxo =
                      new WhirlpoolUtxo(utxo, account, utxoConfig, WhirlpoolUtxoStatus.READY);
                  if (!isFirstFetch) {
                    // set lastActivity when utxo is detected but ignore on first fetch
                    whirlpoolUtxo.getUtxoState().setLastActivity();
                  }
                  whirlpoolWallet.onUtxoDetected(whirlpoolUtxo, isFirstFetch);
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
