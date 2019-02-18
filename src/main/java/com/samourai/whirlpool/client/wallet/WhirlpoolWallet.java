package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.beans.UnspentResponse;
import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
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
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolPoolByBalanceMinDescComparator;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoPriorityComparator;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoMixOrchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoTx0Orchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.MixOrchestrator;
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
import org.bitcoinj.core.TransactionOutPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWallet {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWallet.class);
  private static final int TX0_MIN_CONFIRMATIONS = 1;
  private static final int AUTOMIX_DELAY = 3 * 60 * 1000; // automix rescan delay for premix
  private static final long CACHE_EXPIRY_UTXOS = 60 * 1000; // 1 minute

  private WhirlpoolWalletConfig config;

  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  private IIndexHandler feeIndexHandler;
  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;

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
        whirlpoolWallet.config,
        whirlpoolWallet.tx0Service,
        whirlpoolWallet.bech32Util,
        whirlpoolWallet.whirlpoolClient,
        whirlpoolWallet.feeIndexHandler,
        whirlpoolWallet.depositWallet,
        whirlpoolWallet.premixWallet,
        whirlpoolWallet.postmixWallet);
  }

  public WhirlpoolWallet(
      WhirlpoolWalletConfig config,
      Tx0Service tx0Service,
      Bech32UtilGeneric bech32Util,
      WhirlpoolClient whirlpoolClient,
      IIndexHandler feeIndexHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    this.config = config;

    this.tx0Service = tx0Service;
    this.bech32Util = bech32Util;

    this.whirlpoolClient = whirlpoolClient;

    this.feeIndexHandler = feeIndexHandler;
    this.depositWallet = depositWallet;
    this.premixWallet = premixWallet;
    this.postmixWallet = postmixWallet;

    this.mixOrchestrator =
        new MixOrchestrator(this, config.getMaxClients(), config.getClientDelay());

    if (config.isAutoTx0()) {
      this.autoTx0Orchestrator = Optional.of(new AutoTx0Orchestrator(this, config.getTx0Delay()));
    } else {
      this.autoTx0Orchestrator = Optional.empty();
    }
    if (config.isAutoMix()) {
      int autoMixLoopDelay = AUTOMIX_DELAY * 1000;
      this.autoMixOrchestrator = Optional.of(new AutoMixOrchestrator(this, autoMixLoopDelay));
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

    // remove obsolete utxos, update valid ones
    StreamSupport.stream(currentUtxos)
        .forEach(
            new Consumer<WhirlpoolUtxo>() {
              @Override
              public void accept(WhirlpoolUtxo whirlpoolUtxo) {
                String key = whirlpoolUtxo.getUtxo().toKey();

                UnspentOutput freshUtxo = freshUtxos.get(key);
                if (freshUtxo != null) {
                  // update existing utxo
                  whirlpoolUtxo.setUtxo(freshUtxo);
                } else {
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

  public WhirlpoolUtxo findTx0SpendFrom(int nbOutputsMin, Collection<Pool> poolsByPriority)
      throws Exception { // throws EmptyWalletException, UnconfirmedUtxoException
    Collection<WhirlpoolUtxo> depositUtxosByPriority =
        StreamSupport.stream(getUtxosDeposit(true))
            .sorted(new WhirlpoolUtxoPriorityComparator())
            .collect(Collectors.<WhirlpoolUtxo>toList());

    int feeSatPerByte = config.getSamouraiApi().fetchFees();
    return findTx0SpendFrom(
        nbOutputsMin,
        poolsByPriority,
        depositUtxosByPriority,
        feeSatPerByte,
        null); // throws EmptyWalletException, UnconfirmedUtxoException
  }

  private WhirlpoolUtxo findTx0SpendFrom(
      int nbOutputsMin,
      Collection<Pool> poolsByPriority,
      Collection<WhirlpoolUtxo> depositUtxosByPriority,
      int feeSatPerByte,
      Integer nbOutputsPreferred)
      throws EmptyWalletException, UnconfirmedUtxoException, NotifiableException {

    if (poolsByPriority.isEmpty()) {
      throw new NotifiableException("No pool to spend tx0 from");
    }

    WhirlpoolUtxo unconfirmedUtxo = null;
    for (WhirlpoolUtxo whirlpoolUtxo : depositUtxosByPriority) {
      Pool eligiblePool = whirlpoolUtxo.getPool();
      if (eligiblePool == null) {

        Collection<Pool> eligiblePools = null;
        // find eligible pools for nbOutputsPreferred
        if (nbOutputsPreferred != null) {
          eligiblePools =
              tx0Service.findPools(
                  nbOutputsPreferred, poolsByPriority, whirlpoolUtxo, feeSatPerByte);
        }

        // otherwise, find eligible pools for nbOutputsMin
        if (eligiblePools == null || eligiblePools.isEmpty()) {
          eligiblePools =
              tx0Service.findPools(nbOutputsMin, poolsByPriority, whirlpoolUtxo, feeSatPerByte);
        }

        if (!eligiblePools.isEmpty()) {
          eligiblePool = eligiblePools.iterator().next();
        }
      }

      // check pool
      if (eligiblePool != null) {

        // check confirmation
        if (whirlpoolUtxo.getUtxo().confirmations >= TX0_MIN_CONFIRMATIONS) {

          // set pool
          if (whirlpoolUtxo.getPool() == null) {
            whirlpoolUtxo.setPool(eligiblePool);
          }

          // utxo found
          return whirlpoolUtxo;
        } else {
          // found unconfirmed
          unconfirmedUtxo = whirlpoolUtxo;
        }
      }
    }

    // no confirmed utxo found, but we found unconfirmed utxo
    if (unconfirmedUtxo != null) {
      UnspentOutput utxo = unconfirmedUtxo.getUtxo();
      throw new UnconfirmedUtxoException(utxo);
    }

    // no eligible deposit UTXO found
    long requiredBalance =
        tx0Service.computeSpendFromBalanceMin(
            poolsByPriority.iterator().next(), feeSatPerByte, nbOutputsMin);
    throw new EmptyWalletException("No UTXO found to spend TX0 from", requiredBalance);
  }

  public synchronized Tx0 tx0()
      throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    return tx0(1);
  }

  public synchronized Tx0 tx0(int nbOutputsMin)
      throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    Collection<Pool> poolsByPriority = getPoolsByPriority();
    WhirlpoolUtxo spendFrom =
        findTx0SpendFrom(
            nbOutputsMin, poolsByPriority); // throws UnconfirmedUtxoException, EmptyWalletException
    return tx0(spendFrom);
  }

  public synchronized Tx0 tx0(WhirlpoolUtxo whirlpoolUtxoSpendFrom) throws Exception {
    int feeSatPerByte = config.getSamouraiApi().fetchFees();
    return tx0(whirlpoolUtxoSpendFrom, feeSatPerByte, null);
  }

  public synchronized Tx0 tx0(
      WhirlpoolUtxo whirlpoolUtxoSpendFrom, int feeSatPerByte, Integer nbOutputsPreferred)
      throws Exception {

    // check pool
    Pool pool = whirlpoolUtxoSpendFrom.getPool();
    if (pool == null) {
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO_FAILED, 0);
      whirlpoolUtxoSpendFrom.setError("No pool set");
      throw new NotifiableException("Tx0 failed: no pool set");
    }

    UnspentOutput utxoSpendFrom = whirlpoolUtxoSpendFrom.getUtxo();

    // check confirmations
    if (utxoSpendFrom.confirmations < TX0_MIN_CONFIRMATIONS) {
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO_FAILED, 0);
      whirlpoolUtxoSpendFrom.setError("Minimum confirmation(s) for tx0: " + TX0_MIN_CONFIRMATIONS);
      throw new UnconfirmedUtxoException(utxoSpendFrom);
    }

    whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO, 50);
    try {
      TransactionOutPoint spendFromOutpoint =
          utxoSpendFrom.computeOutpoint(config.getNetworkParameters());
      byte[] spendFromPrivKey =
          depositWallet.getAddressAt(utxoSpendFrom).getECKey().getPrivKeyBytes();
      long spendFromValue = whirlpoolUtxoSpendFrom.getUtxo().value;

      Tx0 tx0 =
          tx0(
              spendFromOutpoint,
              spendFromPrivKey,
              spendFromValue,
              pool,
              feeSatPerByte,
              nbOutputsPreferred);

      // success
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO_SUCCESS, 100);
      whirlpoolUtxoSpendFrom.setMessage("TX0 txid: " + tx0.getTx().getHashAsString());

      return tx0;
    } catch (Exception e) {
      // error
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO_FAILED, 100);
      whirlpoolUtxoSpendFrom.setError(e);
      throw e;
    }
  }

  public synchronized Tx0 tx0(
      TransactionOutPoint spendFromOutpoint,
      byte[] spendFromPrivKey,
      long spendFromValue,
      Pool pool)
      throws Exception {
    int feeSatPerByte = config.getSamouraiApi().fetchFees();
    return tx0(spendFromOutpoint, spendFromPrivKey, spendFromValue, pool, feeSatPerByte, null);
  }

  public synchronized Tx0 tx0(
      TransactionOutPoint spendFromOutpoint,
      byte[] spendFromPrivKey,
      long spendFromValue,
      Pool pool,
      int feeSatPerByte,
      Integer nbOutputsPreferred)
      throws Exception {

    // check balance min
    final long spendFromBalanceMin = tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, 1);
    if (spendFromValue < spendFromBalanceMin) {
      throw new Exception(
          "Insufficient utxo value for Tx0: " + spendFromValue + " < " + spendFromBalanceMin);
    }

    log.info(
        " • Tx0: spendFrom="
            + spendFromOutpoint
            + " ("
            + spendFromValue
            + " sats)"
            + ", poolId="
            + pool.getPoolId()
            + ", nbOutputsPreferred="
            + (nbOutputsPreferred != null ? nbOutputsPreferred : "MAX"));

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
    config.getPushTxService().pushTx(tx0.getTx());

    // refresh utxos
    config.getSamouraiApi().refreshUtxos();
    clearCache(WhirlpoolAccount.DEPOSIT);
    clearCache(WhirlpoolAccount.PREMIX);
    return tx0;
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
      if (config.getPoolIdsByPriority() != null) {
        for (String poolId : config.getPoolIdsByPriority()) {
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
        // biggest balanceMin first
        poolsByPriority =
            StreamSupport.stream(pools.getPools())
                .sorted(new WhirlpoolPoolByBalanceMinDescComparator())
                .collect(Collectors.<Pool>toList());
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
      log.info(
          " • Connecting client to pool: "
              + whirlpoolUtxo.getPool().getPoolId()
              + ", utxo="
              + whirlpoolUtxo);
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
            whirlpoolUtxo.incrementMixsDone();
          }
        };

    int nbMixs = 1;
    try {
      // start mixing (whirlpoolClient will start a new thread)
      MixParams mixParams = computeMixParams(whirlpoolUtxo);
      config.newClient().whirlpool(mixParams, nbMixs, listener);
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
    if (clearCache) {
      clearCache(account);
    }
    Long lastFetchElapsedTime = System.currentTimeMillis() - getLastFetchUtxos(account);
    if (lastFetchElapsedTime >= CACHE_EXPIRY_UTXOS) {
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
    return bech32Util.toBech32(
        depositWallet.getNextAddress(increment), config.getNetworkParameters());
  }

  protected void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {
    if (log.isDebugEnabled()) {
      log.debug("New utxo detected: " + whirlpoolUtxo);
    }
    mixOrchestrator.onUtxoDetected(whirlpoolUtxo);
    if (autoTx0Orchestrator.isPresent()) {
      autoTx0Orchestrator.get().onUtxoDetected(whirlpoolUtxo);
    }
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
