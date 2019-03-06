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
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
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
import com.samourai.whirlpool.protocol.beans.Utxo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java8.util.Optional;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionOutPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWallet {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWallet.class);
  public static final int TX0_MIN_CONFIRMATIONS = 1;
  public static final int MIX_MIN_CONFIRMATIONS = 1;

  private WhirlpoolWalletConfig config;

  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  private IIndexHandler feeIndexHandler;
  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;

  private WhirlpoolWalletCacheData cacheData;

  private MixOrchestrator mixOrchestrator;
  private Optional<AutoTx0Orchestrator> autoTx0Orchestrator;
  private Optional<AutoMixOrchestrator> autoMixOrchestrator;

  // preserve utxo configs through tx0 & mixs
  private Map<String, WhirlpoolUtxoConfig> utxoConfigByTxid;

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

    int loopDelay = config.getRefreshUtxoDelay() * 1000;
    this.mixOrchestrator =
        new MixOrchestrator(loopDelay, this, config.getMaxClients(), config.getClientDelay());

    if (config.isAutoTx0()) {
      this.autoTx0Orchestrator =
          Optional.of(new AutoTx0Orchestrator(loopDelay, this, config.getTx0Delay()));
    } else {
      this.autoTx0Orchestrator = Optional.empty();
    }
    if (config.isAutoMix()) {
      this.autoMixOrchestrator = Optional.of(new AutoMixOrchestrator(loopDelay, this));
    } else {
      this.autoMixOrchestrator = Optional.empty();
    }

    this.utxoConfigByTxid = new HashMap<String, WhirlpoolUtxoConfig>();
    this.clearCache();
  }

  public void clearCache() {
    this.cacheData = new WhirlpoolWalletCacheData(this, config, whirlpoolClient);
  }

  public void clearCache(WhirlpoolAccount account) {
    this.cacheData.clearUtxos(account);
  }

  public Collection<Pool> findPoolsByPreferenceForTx0(long utxoValue, int nbOutputsMin)
      throws Exception {
    return findPoolsByPreferenceForTx0(utxoValue, nbOutputsMin, false);
  }

  public Collection<Pool> findPoolsByPreferenceForTx0(
      long utxoValue, int nbOutputsMin, boolean clearCache) throws Exception {
    // clear cache
    if (clearCache) {
      cacheData.clearPools();
    }

    // find eligible pools
    Collection<Pool> poolsByPreference = getPoolsByPreference();
    int feeSatPerByte = getFeeSatPerByte();
    return tx0Service.findPools(nbOutputsMin, poolsByPreference, utxoValue, feeSatPerByte);
  }

  public WhirlpoolUtxo findTx0SpendFrom(int nbOutputsMin, Collection<Pool> poolsByPreference)
      throws Exception { // throws EmptyWalletException, UnconfirmedUtxoException
    return findTx0SpendFrom(nbOutputsMin, poolsByPreference, getFeeSatPerByte());
  }

  public WhirlpoolUtxo findTx0SpendFrom(
      int nbOutputsMin, Collection<Pool> poolsByPreference, int feeSatPerByte)
      throws Exception { // throws EmptyWalletException, UnconfirmedUtxoException

    Collection<WhirlpoolUtxo> depositUtxosByPriority =
        StreamSupport.stream(getUtxosDeposit(true))
            .sorted(new WhirlpoolUtxoPriorityComparator())
            .collect(Collectors.<WhirlpoolUtxo>toList());

    return findTx0SpendFrom(
        nbOutputsMin,
        poolsByPreference,
        depositUtxosByPriority,
        feeSatPerByte); // throws EmptyWalletException, UnconfirmedUtxoException
  }

  private WhirlpoolUtxo findTx0SpendFrom(
      int nbOutputsMin,
      Collection<Pool> poolsByPreference,
      Collection<WhirlpoolUtxo> depositUtxosByPriority,
      int feeSatPerByte)
      throws EmptyWalletException, Exception, NotifiableException {

    if (poolsByPreference.isEmpty()) {
      throw new NotifiableException("No pool to spend tx0 from");
    }

    WhirlpoolUtxo unconfirmedUtxo = null;
    for (WhirlpoolUtxo whirlpoolUtxo : depositUtxosByPriority) {
      Pool eligiblePool = whirlpoolUtxo.getUtxoConfig().getPool();
      if (eligiblePool == null) {
        Collection<Pool> eligiblePools =
            tx0Service.findPools(
                nbOutputsMin, poolsByPreference, whirlpoolUtxo.getUtxo().value, feeSatPerByte);
        if (!eligiblePools.isEmpty()) {
          eligiblePool = eligiblePools.iterator().next();
        }
      }

      // check pool
      if (eligiblePool != null) {

        // check confirmation
        if (whirlpoolUtxo.getUtxo().confirmations >= TX0_MIN_CONFIRMATIONS) {

          if (whirlpoolUtxo.getUtxoConfig().getPool() == null) {
            // no pool was set => set pool found
            whirlpoolUtxo.getUtxoConfig().setPool(eligiblePool);
            // utxo found
            return whirlpoolUtxo;
          } else {
            // pool was already set => verify pool still eligible
            boolean eligible =
                tx0Service.isTx0Possible(
                    whirlpoolUtxo.getUtxo().value,
                    whirlpoolUtxo.getUtxoConfig().getPool(),
                    feeSatPerByte,
                    nbOutputsMin);
            if (eligible) {
              // still eligible
              return whirlpoolUtxo;
            } else {
              // unset pool
              whirlpoolUtxo.getUtxoConfig().setPool(null);
            }
          }

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
            poolsByPreference.iterator().next(), feeSatPerByte, nbOutputsMin);
    throw new EmptyWalletException("No UTXO found to spend TX0 from", requiredBalance);
  }

  public Tx0 tx0() throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    return tx0(1);
  }

  public Tx0 tx0(int nbOutputsMin)
      throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    Collection<Pool> poolsByPreference = getPoolsByPreference();
    int feeSatPerByte = getFeeSatPerByte();
    WhirlpoolUtxo spendFrom =
        findTx0SpendFrom(
            nbOutputsMin,
            poolsByPreference,
            feeSatPerByte); // throws UnconfirmedUtxoException, EmptyWalletException
    return tx0(spendFrom, feeSatPerByte, config.getTx0MaxOutputs());
  }

  public Tx0 tx0(WhirlpoolUtxo whirlpoolUtxoSpendFrom) throws Exception {
    int feeSatPerByte = getFeeSatPerByte();
    return tx0(whirlpoolUtxoSpendFrom, feeSatPerByte, config.getTx0MaxOutputs());
  }

  public Tx0 tx0(WhirlpoolUtxo whirlpoolUtxoSpendFrom, int feeSatPerByte, Integer maxOutputs)
      throws Exception {

    // check status
    WhirlpoolUtxoStatus utxoStatus = whirlpoolUtxoSpendFrom.getStatus();
    if (!WhirlpoolUtxoStatus.READY.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.TX0_FAILED.equals(utxoStatus)) {
      throw new NotifiableException("Cannot Tx0: utxoStatus=" + utxoStatus);
    }

    // check pool
    Pool pool = whirlpoolUtxoSpendFrom.getUtxoConfig().getPool();
    if (pool == null) {
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_FAILED, 0);
      whirlpoolUtxoSpendFrom.setError("Tx0 failed: no pool set");
      throw new NotifiableException("Tx0 failed: no pool set");
    }

    UnspentOutput utxoSpendFrom = whirlpoolUtxoSpendFrom.getUtxo();

    // check confirmations
    if (utxoSpendFrom.confirmations < TX0_MIN_CONFIRMATIONS) {
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_FAILED, 0);
      whirlpoolUtxoSpendFrom.setError("Minimum confirmation(s) for tx0: " + TX0_MIN_CONFIRMATIONS);
      throw new UnconfirmedUtxoException(utxoSpendFrom);
    }

    whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0, 50);
    try {
      TransactionOutPoint spendFromOutpoint =
          utxoSpendFrom.computeOutpoint(config.getNetworkParameters());
      byte[] spendFromPrivKey =
          depositWallet.getAddressAt(utxoSpendFrom).getECKey().getPrivKeyBytes();
      long spendFromValue = whirlpoolUtxoSpendFrom.getUtxo().value;

      Tx0 tx0 =
          tx0(spendFromOutpoint, spendFromPrivKey, spendFromValue, pool, feeSatPerByte, maxOutputs);

      // success
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_SUCCESS, 100);
      whirlpoolUtxoSpendFrom.setMessage("TX0 txid: " + tx0.getTx().getHashAsString());

      // preserve utxo config
      String tx0Txid = tx0.getTx().getHashAsString();
      addUtxoConfig(whirlpoolUtxoSpendFrom.getUtxoConfig(), tx0Txid);

      return tx0;
    } catch (Exception e) {
      // error
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_FAILED, 100);
      whirlpoolUtxoSpendFrom.setError(e);
      throw e;
    }
  }

  public Tx0 tx0(
      TransactionOutPoint spendFromOutpoint,
      byte[] spendFromPrivKey,
      long spendFromValue,
      Pool pool)
      throws Exception {
    int feeSatPerByte = getFeeSatPerByte();
    return tx0(
        spendFromOutpoint,
        spendFromPrivKey,
        spendFromValue,
        pool,
        feeSatPerByte,
        config.getTx0MaxOutputs());
  }

  public Tx0 tx0(
      TransactionOutPoint spendFromOutpoint,
      byte[] spendFromPrivKey,
      long spendFromValue,
      Pool pool,
      int feeSatPerByte,
      Integer maxOutputs)
      throws Exception {

    Pools pools = getPoolsResponse();

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
            + ", maxOutputs="
            + (maxOutputs != null ? maxOutputs : "*"));

    // run tx0
    Tx0 tx0 =
        tx0Service.tx0(
            spendFromPrivKey,
            spendFromOutpoint,
            depositWallet,
            premixWallet,
            feeIndexHandler,
            feeSatPerByte,
            pools,
            pool,
            maxOutputs);

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
    ClientUtils.sleepRefreshUtxos(config.getNetworkParameters());
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

  public void setPool(WhirlpoolUtxo whirlpoolUtxo, String poolId) throws Exception {
    Pool pool = null;
    if (poolId != null) {
      pool = findPoolById(poolId);
      if (pool == null) {
        throw new NotifiableException("Pool not found: " + poolId);
      }
    }
    whirlpoolUtxo.getUtxoConfig().setPool(pool);
  }

  public void setMixsTarget(WhirlpoolUtxo whirlpoolUtxo, int mixsTarget)
      throws NotifiableException {
    if (mixsTarget < 0) {
      throw new NotifiableException("Invalid mixsTarget: " + mixsTarget);
    }
    whirlpoolUtxo.getUtxoConfig().setMixsTarget(mixsTarget);
  }

  public void mixQueue(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    this.mixOrchestrator.mixQueue(whirlpoolUtxo);
  }

  public void mixStop(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    this.mixOrchestrator.mixStop(whirlpoolUtxo);
  }

  public int getFeeSatPerByte() {
    return cacheData.getFeeSatPerByte();
  }

  public Pools getPoolsResponse() throws Exception {
    return getPoolsResponse(false);
  }

  public Pools getPoolsResponse(boolean clearCache) throws Exception {
    if (clearCache) {
      cacheData.clearPools();
    }
    return cacheData.getPoolsResponse();
  }

  public Collection<Pool> getPoolsAvailable() throws Exception {
    return getPoolsAvailable(false);
  }

  public Collection<Pool> getPoolsAvailable(boolean clearCache) throws Exception {
    return getPoolsResponse(clearCache).getPools();
  }

  public Collection<Pool> getPoolsByPreference() throws Exception {
    return getPoolsByPreference(false);
  }

  public Collection<Pool> getPoolsByPreference(boolean clearCache) throws Exception {
    if (clearCache) {
      cacheData.clearPools();
    }
    return cacheData.getPoolsByPreference();
  }

  public Pool findPoolById(String poolId) throws Exception {
    return findPoolById(poolId, false);
  }

  public Pool findPoolById(String poolId, boolean clearCache) throws Exception {
    return getPoolsResponse(clearCache).findPoolById(poolId);
  }

  public Collection<Pool> findPoolsByPreferenceForPremix(long utxoValue, boolean liquidity)
      throws Exception {
    return findPoolsByPreferenceForPremix(utxoValue, liquidity, false);
  }

  public Collection<Pool> findPoolsByPreferenceForPremix(
      long utxoValue, boolean liquidity, boolean clearCache) throws Exception {
    // clear cache
    if (clearCache) {
      cacheData.clearPools();
    }

    // find eligible pools
    List<Pool> poolsAccepted = new ArrayList<Pool>();
    for (Pool pool : getPoolsByPreference()) {
      if (pool.checkInputBalance(utxoValue, liquidity)) {
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

  public WhirlpoolClient mix(WhirlpoolUtxo whirlpoolUtxo) throws UnconfirmedUtxoException {
    return mix(whirlpoolUtxo, null);
  }

  public WhirlpoolClient mix(
      final WhirlpoolUtxo whirlpoolUtxo, WhirlpoolClientListener notifyListener)
      throws UnconfirmedUtxoException {

    // check confirmations
    if (whirlpoolUtxo.getUtxo().confirmations < MIX_MIN_CONFIRMATIONS) {
      throw new UnconfirmedUtxoException(whirlpoolUtxo.getUtxo());
    }

    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_STARTED, 1);
    if (log.isDebugEnabled()) {
      log.info(
          " • Connecting client to pool: "
              + whirlpoolUtxo.getUtxoConfig().getPool().getPoolId()
              + ", utxo="
              + whirlpoolUtxo);
    } else {
      log.info(
          " • Connecting client to pool: " + whirlpoolUtxo.getUtxoConfig().getPool().getPoolId());
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
            whirlpoolUtxo.getUtxoConfig().incrementMixsDone();

            // preserve utxo config
            addUtxoConfig(whirlpoolUtxo.getUtxoConfig(), mixSuccess.getReceiveUtxo());
          }
        };

    int nbMixs = 1;

    // start mixing (whirlpoolClient will start a new thread)
    MixParams mixParams = computeMixParams(whirlpoolUtxo);
    final WhirlpoolClient mixClient = config.newClient();
    mixClient.whirlpool(mixParams, nbMixs, listener);

    return mixClient;
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
    Pool pool = whirlpoolUtxo.getUtxoConfig().getPool();
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
    return getUtxos(clearCache, WhirlpoolAccount.DEPOSIT);
  }

  public Collection<WhirlpoolUtxo> getUtxosPremix(boolean clearCache) throws Exception {
    return getUtxos(clearCache, WhirlpoolAccount.PREMIX);
  }

  public Collection<WhirlpoolUtxo> getUtxosPostmix(boolean clearCache) throws Exception {
    return getUtxos(clearCache, WhirlpoolAccount.POSTMIX);
  }

  public WhirlpoolUtxo findUtxo(String utxoHash, int utxoIndex) throws Exception {
    return cacheData.findUtxo(utxoHash, utxoIndex, WhirlpoolAccount.values());
  }

  public WhirlpoolUtxo findUtxo(String utxoHash, int utxoIndex, WhirlpoolAccount... accounts)
      throws Exception {
    return cacheData.findUtxo(utxoHash, utxoIndex, accounts);
  }

  public Collection<WhirlpoolUtxo> getUtxos(boolean clearCache, WhirlpoolAccount... accounts)
      throws Exception {
    return cacheData.getUtxos(clearCache, accounts);
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

  private void addUtxoConfig(WhirlpoolUtxoConfig utxoConfig, String txid) {
    utxoConfigByTxid.put(txid, utxoConfig.copy());
  }

  private void addUtxoConfig(WhirlpoolUtxoConfig utxoConfig, Utxo utxo) {
    String key = ClientUtils.utxoToKey(utxo.getHash(), (int) utxo.getIndex());
    utxoConfigByTxid.put(key, utxoConfig.copy());
  }

  private WhirlpoolUtxoConfig findUtxoConfig(UnspentOutput utxo) {
    // search by utxo
    String utxoKey = ClientUtils.utxoToKey(utxo.tx_hash, utxo.tx_output_n);
    WhirlpoolUtxoConfig utxoConfig = utxoConfigByTxid.get(utxoKey);
    if (utxoConfig != null) {
      utxoConfigByTxid.remove(utxoKey);
      return utxoConfig;
    }

    // search by txid
    return utxoConfigByTxid.get(utxo.tx_hash); // keep in map for other utxos (from tx0)
  }

  protected void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {

    // preserve utxo config
    WhirlpoolUtxoConfig utxoConfig = findUtxoConfig(whirlpoolUtxo.getUtxo());
    if (utxoConfig != null) {
      // set utxo config
      whirlpoolUtxo.getUtxoConfig().set(utxoConfig);
    }
    if (log.isDebugEnabled()) {
      log.debug("New utxo detected: " + whirlpoolUtxo);
    }

    // auto-assign pool when possible
    if (whirlpoolUtxo.getUtxoConfig().getPool() == null) {
      try {
        autoAssignPool(whirlpoolUtxo);
      } catch (Exception e) {
        log.error("", e);
      }
    }

    // notify orchestrators
    mixOrchestrator.onUtxoDetected(whirlpoolUtxo);
    if (autoTx0Orchestrator.isPresent()) {
      autoTx0Orchestrator.get().onUtxoDetected(whirlpoolUtxo);
    }
    if (autoMixOrchestrator.isPresent()) {
      autoMixOrchestrator.get().onUtxoDetected(whirlpoolUtxo);
    }
  }

  private void autoAssignPool(WhirlpoolUtxo whirlpoolUtxo) throws Exception {
    Collection<Pool> eligiblePools = null;

    // find eligible pools for tx0
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())) {
      eligiblePools = findPoolsByPreferenceForTx0(whirlpoolUtxo.getUtxo().value, 1);
    }

    // find eligible pools for mix
    else if (WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())
        || WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())) {
      boolean liquidity = WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount());
      eligiblePools = findPoolsByPreferenceForPremix(whirlpoolUtxo.getUtxo().value, liquidity);
    }

    // auto-assign pool by preference when found
    if (eligiblePools != null) {
      if (!eligiblePools.isEmpty()) {
        whirlpoolUtxo.getUtxoConfig().setPool(eligiblePools.iterator().next());
      } else {
        log.warn("No pool for this utxo balance: " + whirlpoolUtxo.toString());
        whirlpoolUtxo.setError("No pool for this utxo balance");
      }
    }
  }

  protected void onUtxoRemoved(WhirlpoolUtxo whirlpoolUtxo) {}

  protected void onUtxoUpdated(WhirlpoolUtxo whirlpoolUtxo, UnspentOutput oldUtxo) {
    int oldConfirmations = oldUtxo.confirmations;
    int freshConfirmations = whirlpoolUtxo.getUtxo().confirmations;

    // notify autoTx0Orchestrator on TX0_MIN_CONFIRMATIONS
    if (autoTx0Orchestrator.isPresent()
        && wasConfirmed(TX0_MIN_CONFIRMATIONS, oldConfirmations, freshConfirmations)) {
      autoTx0Orchestrator.get().onUtxoConfirmed(whirlpoolUtxo);
    }

    // notify mixOrchestrator on MIX_MIN_CONFIRMATIONS
    if (wasConfirmed(MIX_MIN_CONFIRMATIONS, oldConfirmations, freshConfirmations)) {
      mixOrchestrator.onUtxoConfirmed(whirlpoolUtxo);
    }
  }

  private boolean wasConfirmed(int minConfirmations, int oldConfirmations, int freshConfirmations) {
    return oldConfirmations < minConfirmations && freshConfirmations >= minConfirmations;
  }

  public void onEmptyWalletException(EmptyWalletException e) {
    String depositAddress = getDepositAddress(false);
    String message = e.getMessageDeposit(depositAddress);
    log.error(message);
  }
}
