package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.api.client.beans.UnspentResponse;
import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.Bip84PostmixHandler;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.IPremixHandler;
import com.samourai.whirlpool.client.mix.handler.PremixHandler;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.MixOrchestratorState;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoMixOrchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoTx0Orchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.MixOrchestrator;
import com.samourai.whirlpool.client.wallet.pushTx.PushTxService;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java8.util.Optional;
import java8.util.function.Consumer;
import java8.util.function.Predicate;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWallet {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWallet.class);
  private static final long CACHE_EXPIRY_UTXOS = 60 * 1000; // 1 minute

  private NetworkParameters params;
  private SamouraiApi samouraiApi;
  private PushTxService pushTxService;
  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;
  private WhirlpoolClient whirlpoolClient;
  private WhirlpoolClientConfig whirlpoolClientConfig;

  private IIndexHandler feeIndexHandler;
  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;
  private int maxClients;
  private int clientDelay;
  private int autoTx0Delay;
  private int autoMixDelay;
  private Collection<String> poolIdsByPriority;

  // TODO cache expiry
  private Collection<Pool> poolsByPriority;
  private Pools pools;
  private Map<String, WhirlpoolUtxo> utxos;
  private Map<WhirlpoolAccount, Long> lastFetchUtxos;

  private MixOrchestrator mixOrchestrator;
  private Optional<AutoTx0Orchestrator> autoTx0Orchestrator;
  private Optional<AutoMixOrchestrator> autoMixOrchestrator;

  protected WhirlpoolWallet(WhirlpoolWallet whirlpoolWallet) {
    this(
        whirlpoolWallet.params,
        whirlpoolWallet.samouraiApi,
        whirlpoolWallet.pushTxService,
        whirlpoolWallet.tx0Service,
        whirlpoolWallet.bech32Util,
        whirlpoolWallet.whirlpoolClient,
        whirlpoolWallet.whirlpoolClientConfig,
        whirlpoolWallet.maxClients,
        whirlpoolWallet.clientDelay,
        whirlpoolWallet.autoTx0Delay,
        whirlpoolWallet.autoMixDelay,
        whirlpoolWallet.poolIdsByPriority,
        whirlpoolWallet.feeIndexHandler,
        whirlpoolWallet.depositWallet,
        whirlpoolWallet.premixWallet,
        whirlpoolWallet.postmixWallet);
  }

  public WhirlpoolWallet(
      NetworkParameters params,
      SamouraiApi samouraiApi,
      PushTxService pushTxService,
      Tx0Service tx0Service,
      Bech32UtilGeneric bech32Util,
      WhirlpoolClient whirlpoolClient,
      WhirlpoolClientConfig whirlpoolClientConfig,
      int maxClients,
      int clientDelay,
      int autoTx0Delay, // 0 to disable
      int autoMixDelay, // 0 to disable
      Collection<String> poolIdsByPriority,
      IIndexHandler feeIndexHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    this.params = params;
    this.samouraiApi = samouraiApi;
    this.pushTxService = pushTxService;
    this.tx0Service = tx0Service;
    this.bech32Util = bech32Util;
    this.whirlpoolClient = whirlpoolClient;
    this.whirlpoolClientConfig = whirlpoolClientConfig;
    this.maxClients = maxClients;
    this.clientDelay = clientDelay;

    this.feeIndexHandler = feeIndexHandler;
    this.depositWallet = depositWallet;
    this.premixWallet = premixWallet;
    this.postmixWallet = postmixWallet;

    this.autoTx0Delay = autoTx0Delay;
    this.autoMixDelay = autoMixDelay;
    this.poolIdsByPriority = poolIdsByPriority;

    this.mixOrchestrator = new MixOrchestrator(this, maxClients, clientDelay);

    if (autoTx0Delay > 0) {
      int nbOutputsPreferred = maxClients;
      this.autoTx0Orchestrator =
          Optional.of(
              new AutoTx0Orchestrator(
                  tx0Service,
                  samouraiApi,
                  this,
                  this.mixOrchestrator,
                  autoTx0Delay * 1000,
                  nbOutputsPreferred));
    } else {
      this.autoTx0Orchestrator = Optional.empty();
    }
    if (autoMixDelay > 0) {
      this.autoMixOrchestrator = Optional.of(new AutoMixOrchestrator(this, autoMixDelay * 1000));
    } else {
      this.autoMixOrchestrator = Optional.empty();
    }
    this.clearCache();
  }

  private void fetchPools() throws Exception {
    pools = whirlpoolClient.fetchPools();
  }

  public synchronized void clearCache() {
    this.utxos = new HashMap<String, WhirlpoolUtxo>();
    this.lastFetchUtxos = new HashMap<WhirlpoolAccount, Long>();
  }

  public synchronized void clearCache(WhirlpoolAccount account) {
    this.lastFetchUtxos.put(account, 0L);
  }

  private synchronized void fetchUtxos(WhirlpoolAccount account) throws Exception {
    // fetch new utxos
    Bip84ApiWallet wallet = getWallet(account);
    List<UnspentOutput> fetchedUtxos = wallet.fetchUtxos();
    if (log.isDebugEnabled()) {
      log.debug("Fetching utxos from " + account + "... " + fetchedUtxos.size() + " utxos found");
      ClientUtils.logUtxos(fetchedUtxos);
    }
    final Map<String, UnspentOutput> freshUtxos = new HashMap<String, UnspentOutput>();
    for (UnspentOutput utxo : fetchedUtxos) {
      freshUtxos.put(utxo.toKey(), utxo);
    }

    // replace utxos
    replaceUtxos(account, freshUtxos);

    lastFetchUtxos.put(account, System.currentTimeMillis());
  }

  private void replaceUtxos(
      final WhirlpoolAccount account, final Map<String, UnspentOutput> freshUtxos) {
    Collection<WhirlpoolUtxo> currentUtxos = findUtxos(account);

    // remove obsolete utxos, keep valid ones
    StreamSupport.stream(currentUtxos)
        .forEach(
            new Consumer<WhirlpoolUtxo>() {
              @Override
              public void accept(WhirlpoolUtxo whirlpoolUtxo) {
                String key = whirlpoolUtxo.getUtxo().toKey();
                if (!freshUtxos.containsKey(key)) {
                  // remove obsolete
                  utxos.remove(key);
                  onUtxoRemoved(whirlpoolUtxo);
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
                if (!utxos.containsKey(key)) {
                  // add missing
                  WhirlpoolUtxo whirlpoolUtxo =
                      new WhirlpoolUtxo(utxo, account, WhirlpoolUtxoStatus.READY);
                  utxos.put(key, whirlpoolUtxo);
                  onUtxoDetected(whirlpoolUtxo);
                }
              }
            });
  }

  public synchronized Tx0 tx0(
      Pool pool, int nbOutputsPreferred, WhirlpoolUtxo whirlpoolUtxoSpendFrom) throws Exception {
    int feeSatPerByte = samouraiApi.fetchFees();
    return tx0(pool, nbOutputsPreferred, whirlpoolUtxoSpendFrom, feeSatPerByte);
  }

  public synchronized Tx0 tx0(
      Pool pool, int nbOutputsPreferred, WhirlpoolUtxo whirlpoolUtxoSpendFrom, int feeSatPerByte)
      throws Exception {
    whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO, 50);
    try {
      UnspentOutput utxoSpendFrom = whirlpoolUtxoSpendFrom.getUtxo();

      // check balance min
      final long spendFromBalanceMin =
          tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, 1);
      if (utxoSpendFrom.value < spendFromBalanceMin) {
        throw new Exception("Insufficient utxo value for Tx0 with " + utxoSpendFrom.toString());
      }

      log.info(
          " • Tx0: poolId="
              + pool.getPoolId()
              + ", utxoSpendFrom="
              + utxoSpendFrom
              + ", nbOutputsPreferred="
              + nbOutputsPreferred);

      // spend from
      TransactionOutPoint spendFromOutpoint = utxoSpendFrom.computeOutpoint(params);
      byte[] spendFromPrivKey =
          depositWallet.getAddressAt(utxoSpendFrom).getECKey().getPrivKeyBytes();

      // run tx0
      Tx0 tx0 =
          tx0Service.tx0(
              spendFromPrivKey,
              spendFromOutpoint,
              depositWallet,
              premixWallet,
              feeIndexHandler,
              feeSatPerByte,
              getPools(),
              pool,
              nbOutputsPreferred);

      log.info(
          " • Tx0 result: txid="
              + tx0.getTx().getHashAsString()
              + ", nbPremixs="
              + tx0.getPremixUtxos().size());
      if (log.isDebugEnabled()) {
        log.debug(tx0.getTx().toString());
      }

      // pushTx
      pushTxService.pushTx(tx0.getTx());

      // success
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO_SUCCESS, 100);
      whirlpoolUtxoSpendFrom.setMessage("TX0 txid: " + tx0.getTx().getHashAsString());

      // refresh utxos
      samouraiApi.refreshUtxos();
      clearCache(WhirlpoolAccount.DEPOSIT);
      clearCache(WhirlpoolAccount.PREMIX);
      return tx0;
    } catch (Exception e) {
      // error
      whirlpoolUtxoSpendFrom.setError(e);
      throw e;
    }
  }

  public void start() {
    if (isStarted()) {
      log.warn("NOT starting WhirlpoolWallet: already started");
      return;
    }
    log.info(" • Starting WhirlpoolWallet");

    // reset utxos
    clearCache();

    this.mixOrchestrator.start();
    if (this.autoTx0Orchestrator.isPresent()) {
      this.autoTx0Orchestrator.get().start();
    }
    if (this.autoMixOrchestrator.isPresent()) {
      this.autoMixOrchestrator.get().start();
    }
  }

  public void stop() {
    if (!isStarted()) {
      log.warn("NOT stopping WhirlpoolWallet: not started");
      return;
    }
    log.info(" • Stopping WhirlpoolWallet");
    this.mixOrchestrator.stop();
    if (this.autoTx0Orchestrator.isPresent()) {
      this.autoTx0Orchestrator.get().stop();
    }
    if (this.autoMixOrchestrator.isPresent()) {
      this.autoMixOrchestrator.get().stop();
    }

    // reset utxos
    clearCache();
  }

  public void mixQueue(WhirlpoolUtxo whirlpoolUtxo) {
    this.mixOrchestrator.mixQueue(whirlpoolUtxo);
  }

  public Pools getPools() throws Exception {
    if (pools == null) {
      fetchPools();
    }
    return pools;
  }

  public Collection<Pool> getPoolsByPriority() throws Exception {
    if (poolsByPriority == null) {
      Pools pools = getPools();

      // add pools by priority
      poolsByPriority = new LinkedList<Pool>();
      if (poolIdsByPriority != null) {
        for (String poolId : poolIdsByPriority) {
          Pool pool = pools.findPoolById(poolId);
          if (pool != null) {
            poolsByPriority.add(pool);
          }
        }
      }

      // fallback
      if (poolsByPriority.isEmpty()) {
        if (log.isDebugEnabled()) {
          log.debug("getPoolsByPriority: no priority defined, using all pools");
        }
        poolsByPriority = pools.getPools();
      }
    }
    return poolsByPriority;
  }

  public Collection<Pool> findPoolsByPriorityForPremix(long utxoValue) throws Exception {
    List<Pool> poolsAccepted = new ArrayList<Pool>();
    // pools ordered by denomination DESC
    for (Pool pool : getPoolsByPriority()) {
      long balanceMin =
          WhirlpoolProtocol.computeInputBalanceMin(
              pool.getDenomination(), false, pool.getMinerFeeMin());
      long balanceMax =
          WhirlpoolProtocol.computeInputBalanceMax(
              pool.getDenomination(), false, pool.getMinerFeeMax());
      if (utxoValue >= balanceMin && utxoValue <= balanceMax) {
        poolsAccepted.add(pool);
      }
    }
    return poolsAccepted;
  }

  protected Bip84ApiWallet getWalletDeposit() {
    return depositWallet;
  }

  protected Bip84ApiWallet getWalletPremix() {
    return premixWallet;
  }

  protected Bip84ApiWallet getWalletPostmix() {
    return postmixWallet;
  }

  protected Bip84ApiWallet getWallet(WhirlpoolAccount account) {
    switch (account) {
      case DEPOSIT:
        return depositWallet;
      case PREMIX:
        return premixWallet;
      case POSTMIX:
        return postmixWallet;
      default:
        {
          log.error("Unknown account for getWallet(): " + account);
          return null;
        }
    }
  }

  public WhirlpoolClientListener mix(WhirlpoolUtxo whirlpoolUtxo) {
    return mix(whirlpoolUtxo, null);
  }

  public WhirlpoolClientListener mix(
      final WhirlpoolUtxo whirlpoolUtxo, WhirlpoolClientListener notifyListener) {
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_STARTED, 1);
    if (log.isDebugEnabled()) {
      log.debug(" • Connecting client: utxo=" + whirlpoolUtxo);
    } else {
      log.info(" • Connecting client to pool: " + whirlpoolUtxo.getPool().getPoolId());
    }

    WhirlpoolClientListener listener =
        new LoggingWhirlpoolClientListener(notifyListener) {
          @Override
          protected void logInfo(String message) {
            super.logInfo(message);
            whirlpoolUtxo.setMessage(message);
          }

          @Override
          protected void logError(String message) {
            super.logError(message);
            whirlpoolUtxo.setError(message);
          }

          @Override
          public void progress(
              int currentMix,
              int nbMixs,
              MixStep step,
              String stepInfo,
              int stepNumber,
              int nbSteps) {
            super.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
            int progressPercent = Math.round(stepNumber * 100 / nbSteps);
            whirlpoolUtxo.setProgress(progressPercent, step.name());
          }

          @Override
          public void fail(int currentMix, int nbMixs) {
            super.fail(currentMix, nbMixs);
            whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_FAILED);
            whirlpoolUtxo.setError("Mix " + currentMix + "/" + nbMixs + " failed");
          }

          @Override
          public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
            super.mixSuccess(currentMix, nbMixs, mixSuccess);
            whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_SUCCESS, 100);
          }
        };

    int nbMixs = 1;
    try {
      // start mixing (whirlpoolClient will start a new thread)
      MixParams mixParams = computeMixParams(whirlpoolUtxo);
      whirlpoolClientConfig.newClient().whirlpool(mixParams, nbMixs, listener);
    } catch (Exception e) {
      whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_FAILED);
      whirlpoolUtxo.setError(e);
      log.error(" • ERROR connecting client: " + e.getMessage(), e);
      listener.fail(1, nbMixs);
    }
    return listener;
  }

  private IPremixHandler computePremixHandler(WhirlpoolUtxo whirlpoolUtxo) {
    HD_Address premixAddress =
        getWallet(whirlpoolUtxo.getAccount()).getAddressAt(whirlpoolUtxo.getUtxo());
    ECKey premixKey = premixAddress.getECKey();

    UnspentResponse.UnspentOutput premixOrPostmixUtxo = whirlpoolUtxo.getUtxo();
    UtxoWithBalance utxoWithBalance =
        new UtxoWithBalance(
            premixOrPostmixUtxo.tx_hash,
            premixOrPostmixUtxo.tx_output_n,
            premixOrPostmixUtxo.value);
    return new PremixHandler(utxoWithBalance, premixKey);
  }

  public IPostmixHandler computePostmixHandler() {
    return new Bip84PostmixHandler(getWalletPostmix());
  }

  private MixParams computeMixParams(WhirlpoolUtxo whirlpoolUtxo) {
    IPremixHandler premixHandler = computePremixHandler(whirlpoolUtxo);
    IPostmixHandler postmixHandler = computePostmixHandler();
    Pool pool = whirlpoolUtxo.getPool();
    return new MixParams(pool.getPoolId(), pool.getDenomination(), premixHandler, postmixHandler);
  }

  public Collection<WhirlpoolUtxo> getUtxosDeposit() throws Exception {
    return getUtxosDeposit(false);
  }

  public Collection<WhirlpoolUtxo> getUtxosPremix() throws Exception {
    return getUtxosPremix(false);
  }

  public Collection<WhirlpoolUtxo> getUtxosPostmix() throws Exception {
    return getUtxosPostmix(false);
  }

  public Collection<WhirlpoolUtxo> getUtxosDeposit(boolean clearCache) throws Exception {
    return getUtxos(WhirlpoolAccount.DEPOSIT, clearCache);
  }

  public Collection<WhirlpoolUtxo> getUtxosPremix(boolean clearCache) throws Exception {
    return getUtxos(WhirlpoolAccount.PREMIX, clearCache);
  }

  public Collection<WhirlpoolUtxo> getUtxosPostmix(boolean clearCache) throws Exception {
    return getUtxos(WhirlpoolAccount.POSTMIX, clearCache);
  }

  public Collection<WhirlpoolUtxo> getUtxos(WhirlpoolAccount account, boolean clearCache)
      throws Exception {
    Long lastFetchElapsedTime = System.currentTimeMillis() - getLastFetchUtxos(account);
    if (clearCache || lastFetchElapsedTime >= CACHE_EXPIRY_UTXOS) {
      if (log.isDebugEnabled()) {
        log.debug(
            "getUtxos("
                + account
                + ") -> fetch: clearCache="
                + clearCache
                + ", lastFetchElapsedTime="
                + lastFetchElapsedTime);
      }
      fetchUtxos(account);
    }
    return findUtxos(account);
  }

  private long getLastFetchUtxos(WhirlpoolAccount account) {
    Long lastFetch = lastFetchUtxos.get(account);
    if (lastFetch == null) {
      lastFetch = 0L;
    }
    return lastFetch;
  }

  private Collection<WhirlpoolUtxo> findUtxos(final WhirlpoolAccount account) {
    return StreamSupport.stream(utxos.values())
        .filter(
            new Predicate<WhirlpoolUtxo>() {
              @Override
              public boolean test(WhirlpoolUtxo whirlpoolUtxo) {
                return whirlpoolUtxo.getAccount().equals(account);
              }
            })
        .collect(Collectors.<WhirlpoolUtxo>toList());
  }

  public boolean isStarted() {
    return mixOrchestrator.isStarted();
  }

  public WhirlpoolWalletState getState() {
    MixOrchestratorState mixState = mixOrchestrator.getState();
    return new WhirlpoolWalletState(isStarted(), mixState);
  }

  public String getDepositAddress(boolean increment) {
    return bech32Util.toBech32(depositWallet.getNextAddress(increment), params);
  }

  protected void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    mixOrchestrator.onUtxoDetected(whirlpoolUtxo);
    if (autoMixOrchestrator.isPresent()) {
      autoMixOrchestrator.get().onUtxoDetected(whirlpoolUtxo);
    }
  }

  protected void onUtxoRemoved(WhirlpoolUtxo whirlpoolUtxo) {}

  public void onEmptyWalletException(EmptyWalletException e) {
    String depositAddress = getDepositAddress(false);
    String message = e.getMessageDeposit(depositAddress);
    log.error(message);
  }
}
