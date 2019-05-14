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
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoMixOrchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoTx0Orchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.MixOrchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.PersistOrchestrator;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.UtxoWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import com.samourai.whirlpool.protocol.beans.Utxo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java8.util.Lists;
import java8.util.Optional;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionOutPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWallet {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWallet.class);
  private static final String INDEX_FEE = "fee";

  public static final int TX0_MIN_CONFIRMATIONS = 1;
  public static final int MIX_MIN_CONFIRMATIONS = 1;

  private WhirlpoolWalletConfig config;

  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  private WhirlpoolWalletPersistHandler walletPersistHandler;
  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;

  private WhirlpoolWalletCacheData cacheData;

  private PersistOrchestrator persistOrchestrator;
  protected MixOrchestrator mixOrchestrator;
  private Optional<AutoTx0Orchestrator> autoTx0Orchestrator;
  private Optional<AutoMixOrchestrator> autoMixOrchestrator;

  protected WhirlpoolWallet(WhirlpoolWallet whirlpoolWallet) {
    this(
        whirlpoolWallet.config,
        whirlpoolWallet.tx0Service,
        whirlpoolWallet.bech32Util,
        whirlpoolWallet.whirlpoolClient,
        whirlpoolWallet.walletPersistHandler,
        whirlpoolWallet.depositWallet,
        whirlpoolWallet.premixWallet,
        whirlpoolWallet.postmixWallet);
  }

  public WhirlpoolWallet(
      WhirlpoolWalletConfig config,
      Tx0Service tx0Service,
      Bech32UtilGeneric bech32Util,
      WhirlpoolClient whirlpoolClient,
      WhirlpoolWalletPersistHandler walletPersistHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    this.config = config;

    this.tx0Service = tx0Service;
    this.bech32Util = bech32Util;

    this.whirlpoolClient = whirlpoolClient;

    this.walletPersistHandler = walletPersistHandler;
    this.depositWallet = depositWallet;
    this.premixWallet = premixWallet;
    this.postmixWallet = postmixWallet;

    this.persistOrchestrator =
        new PersistOrchestrator(
            config.getPersistDelay() * 1000, this, config.getPersistCleanDelay() * 1000);
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

    this.clearCache();

    this.walletPersistHandler.loadUtxoConfigs(this);
  }

  public void clearCache() {
    this.cacheData = new WhirlpoolWalletCacheData(this, config, whirlpoolClient);
  }

  public void clearCache(WhirlpoolAccount account) {
    this.cacheData.clearUtxos(account);
  }

  public Collection<Pool> findPoolsForTx0(long utxoValue, int nbOutputsMin, Tx0FeeTarget feeTarget)
      throws Exception {
    return findPoolsForTx0(utxoValue, nbOutputsMin, feeTarget, false);
  }

  public Collection<Pool> findPoolsForTx0(
      long utxoValue, int nbOutputsMin, Tx0FeeTarget feeTarget, boolean clearCache)
      throws Exception {
    // clear cache
    if (clearCache) {
      cacheData.clearPools();
    }

    // find eligible pools
    Collection<Pool> poolsAvailable = getPoolsAvailable();
    int fee = getFee(feeTarget);
    return tx0Service.findPools(nbOutputsMin, poolsAvailable, utxoValue, fee, getFeePremix());
  }

  public WhirlpoolUtxo findTx0SpendFrom(int nbOutputsMin, Pool pool, Tx0FeeTarget feeTarget)
      throws Exception { // throws EmptyWalletException, UnconfirmedUtxoException
    return findTx0SpendFrom(nbOutputsMin, pool, feeTarget, getFeePremix());
  }

  private WhirlpoolUtxo findTx0SpendFrom(
      int nbOutputsMin, Pool pool, Tx0FeeTarget feeTarget, int feePremix)
      throws Exception { // throws EmptyWalletException, UnconfirmedUtxoException

    Collection<WhirlpoolUtxo> depositUtxosByPriority =
        StreamSupport.stream(getUtxosDeposit(true))
            .sorted(new WhirlpoolUtxoPriorityComparator())
            .collect(Collectors.<WhirlpoolUtxo>toList());

    int feeTx0 = getFee(feeTarget);

    return findTx0SpendFrom(
        nbOutputsMin,
        pool,
        depositUtxosByPriority,
        feeTx0,
        feePremix); // throws EmptyWalletException, UnconfirmedUtxoException
  }

  private WhirlpoolUtxo findTx0SpendFrom(
      int nbOutputsMin,
      Pool pool,
      Collection<WhirlpoolUtxo> depositUtxosByPriority,
      int feeTx0,
      int feePremix)
      throws EmptyWalletException, Exception, NotifiableException {

    if (pool == null) {
      throw new NotifiableException("No pool to spend tx0 from");
    }

    WhirlpoolUtxo unconfirmedUtxo = null;
    for (WhirlpoolUtxo whirlpoolUtxo : depositUtxosByPriority) {
      Pool whirlpoolUtxoPool = findPoolById(whirlpoolUtxo.getUtxoConfig().getPoolId());
      Pool eligiblePool = whirlpoolUtxoPool;
      if (eligiblePool == null) {
        Collection<Pool> eligiblePools =
            tx0Service.findPools(
                nbOutputsMin, Lists.of(pool), whirlpoolUtxo.getUtxo().value, feeTx0, feePremix);
        if (!eligiblePools.isEmpty()) {
          eligiblePool = eligiblePools.iterator().next();
        }
      }

      // check pool
      if (eligiblePool != null) {

        // check confirmation
        if (whirlpoolUtxo.getUtxo().confirmations >= TX0_MIN_CONFIRMATIONS) {

          if (whirlpoolUtxoPool == null) {
            // no pool was set => set pool found
            whirlpoolUtxo.getUtxoConfig().setPoolId(eligiblePool.getPoolId());
            // utxo found
            return whirlpoolUtxo;
          } else {
            // pool was already set => verify pool still eligible
            boolean eligible =
                tx0Service.isTx0Possible(
                    whirlpoolUtxo.getUtxo().value,
                    whirlpoolUtxoPool,
                    feeTx0,
                    feePremix,
                    nbOutputsMin);
            if (eligible) {
              // still eligible
              return whirlpoolUtxo;
            } else {
              // unset pool
              whirlpoolUtxo.getUtxoConfig().setPoolId(null);
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
        tx0Service.computeSpendFromBalanceMin(pool, feeTx0, feePremix, nbOutputsMin);
    throw new EmptyWalletException("No UTXO found to spend TX0 from", requiredBalance);
  }

  public long computeTx0SpendFromBalanceMin(Pool pool, Tx0FeeTarget feeTarget, int nbPremix) {
    int feeTx0 = getFee(feeTarget);
    return tx0Service.computeSpendFromBalanceMin(pool, feeTx0, getFeePremix(), nbPremix);
  }

  public Tx0 autoTx0() throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    String poolId = config.getAutoTx0PoolId();
    Tx0FeeTarget feeTarget = config.getAutoTx0FeeTarget();
    Pool pool = findPoolById(poolId);
    if (pool == null) {
      throw new NotifiableException(
          "No pool found for autoTx0 (autoTx0 = " + (poolId != null ? poolId : "null") + ")");
    }
    return tx0(pool, feeTarget);
  }

  public Tx0 tx0(Pool pool, Tx0FeeTarget feeTarget)
      throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    return tx0(pool, feeTarget, 1);
  }

  public Tx0 tx0(Pool pool, Tx0FeeTarget feeTarget, int nbOutputsMin)
      throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    WhirlpoolUtxo spendFrom =
        findTx0SpendFrom(
            nbOutputsMin,
            pool,
            feeTarget,
            getFeePremix()); // throws UnconfirmedUtxoException, EmptyWalletException
    return tx0(spendFrom, feeTarget, config.getTx0MaxOutputs());
  }

  public Tx0 tx0(WhirlpoolUtxo whirlpoolUtxoSpendFrom, Tx0FeeTarget feeTarget) throws Exception {
    return tx0(whirlpoolUtxoSpendFrom, feeTarget, config.getTx0MaxOutputs());
  }

  public Tx0 tx0(WhirlpoolUtxo whirlpoolUtxoSpendFrom, Tx0FeeTarget feeTarget, Integer maxOutputs)
      throws Exception {

    // check status
    WhirlpoolUtxoStatus utxoStatus = whirlpoolUtxoSpendFrom.getStatus();
    if (!WhirlpoolUtxoStatus.READY.equals(utxoStatus)
        && !WhirlpoolUtxoStatus.TX0_FAILED.equals(utxoStatus)) {
      throw new NotifiableException("Cannot Tx0: utxoStatus=" + utxoStatus);
    }

    // check pool
    Pool pool = findPoolById(whirlpoolUtxoSpendFrom.getUtxoConfig().getPoolId());
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

      int feeTx0 = getFee(feeTarget);
      int feePremix = getFeePremix();
      Tx0 tx0 =
          tx0(
              spendFromOutpoint,
              spendFromPrivKey,
              spendFromValue,
              pool,
              feeTx0,
              feePremix,
              maxOutputs);

      // success
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_SUCCESS, 100);
      whirlpoolUtxoSpendFrom.setMessage("TX0 txid: " + tx0.getTx().getHashAsString());

      // preserve utxo config
      String tx0Txid = tx0.getTx().getHashAsString();
      setUtxoConfig(whirlpoolUtxoSpendFrom.getUtxoConfig().copy(), tx0Txid);

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
      Pool pool,
      Tx0FeeTarget feeTarget)
      throws Exception {
    int feeTx0 = getFee(feeTarget);
    return tx0(
        spendFromOutpoint,
        spendFromPrivKey,
        spendFromValue,
        pool,
        feeTx0,
        getFeePremix(),
        config.getTx0MaxOutputs());
  }

  public Tx0 tx0(
      TransactionOutPoint spendFromOutpoint,
      byte[] spendFromPrivKey,
      long spendFromValue,
      Pool pool,
      int feeTx0,
      int feePremix,
      Integer maxOutputs)
      throws Exception {

    Pools pools = getPoolsResponse();

    // check balance min
    final long spendFromBalanceMin =
        tx0Service.computeSpendFromBalanceMin(pool, feeTx0, feePremix, 1);
    if (spendFromValue < spendFromBalanceMin) {
      throw new NotifiableException(
          "Insufficient utxo value for Tx0: " + spendFromValue + " < " + spendFromBalanceMin);
    }

    log.info(
        " • Tx0: spendFrom="
            + spendFromOutpoint
            + ", feeTx0="
            + feeTx0
            + ", feePremix="
            + feePremix
            + ", poolId="
            + pool.getPoolId()
            + ", maxOutputs="
            + (maxOutputs != null ? maxOutputs : "*"));

    // run tx0
    IIndexHandler feeIndexHandler = walletPersistHandler.getIndexHandler(INDEX_FEE);
    int initialFeeIndice = feeIndexHandler.get();
    int initialPremixIndex = premixWallet.getIndexHandler().get();
    try {
      Tx0 tx0 =
          tx0Service.tx0(
              spendFromPrivKey,
              spendFromOutpoint,
              depositWallet,
              premixWallet,
              feeIndexHandler,
              feeTx0,
              feePremix,
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
    } catch (Exception e) {
      // revert indexs
      feeIndexHandler.set(initialFeeIndice);
      premixWallet.getIndexHandler().set(initialPremixIndex);
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

    persistOrchestrator.start();
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
    persistOrchestrator.stop();

    // reset utxos
    clearCache();
  }

  public void setPool(WhirlpoolUtxo whirlpoolUtxo, String poolId) throws Exception {
    // check pool exists
    Pool pool = null;
    if (poolId != null) {
      pool = findPoolById(poolId);
      if (pool == null) {
        throw new NotifiableException("Pool not found: " + poolId);
      }
      poolId = pool.getPoolId();
    }
    // set pool
    whirlpoolUtxo.getUtxoConfig().setPoolId(poolId);
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

  public int getFee(Tx0FeeTarget feeTarget) {
    return cacheData.getFeeSatPerByte(feeTarget.getFeeTarget());
  }

  public int getFeePremix() {
    return cacheData.getFeeSatPerByte(config.getFeeTargetPremix());
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
    if (clearCache) {
      cacheData.clearPools();
    }
    return cacheData.getPools();
  }

  public Pool findPoolById(String poolId) throws Exception {
    if (poolId == null) {
      return null;
    }
    return findPoolById(poolId, false);
  }

  public Pool findPoolById(String poolId, boolean clearCache) throws Exception {
    for (Pool pool : getPoolsAvailable(clearCache)) {
      if (pool.getPoolId().equals(poolId)) {
        return pool;
      }
    }
    return null;
  }

  public Collection<Pool> findPoolsForPremix(long utxoValue, boolean liquidity) throws Exception {
    return findPoolsForPremix(utxoValue, liquidity, false);
  }

  public Collection<Pool> findPoolsForPremix(long utxoValue, boolean liquidity, boolean clearCache)
      throws Exception {
    // clear cache
    if (clearCache) {
      cacheData.clearPools();
    }

    // find eligible pools
    List<Pool> poolsAccepted = new ArrayList<Pool>();
    for (Pool pool : getPoolsAvailable()) {
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

  public WhirlpoolClient mix(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    return mix(whirlpoolUtxo, null);
  }

  public WhirlpoolClient mix(
      final WhirlpoolUtxo whirlpoolUtxo, WhirlpoolClientListener notifyListener)
      throws NotifiableException {

    // check confirmations
    if (whirlpoolUtxo.getUtxo().confirmations < MIX_MIN_CONFIRMATIONS) {
      throw new UnconfirmedUtxoException(whirlpoolUtxo.getUtxo());
    }

    // check pool
    String poolId = whirlpoolUtxo.getUtxoConfig().getPoolId();
    if (poolId == null) {
      log.error(
          "Cannot mix: no pool set: " + whirlpoolUtxo + " ; " + whirlpoolUtxo.getUtxoConfig());
      throw new NotifiableException("Cannot mix: no pool set");
    }
    Pool pool = null;
    try {
      pool = findPoolById(poolId);
    } catch (Exception e) {
      log.error("", e);
    }
    if (pool == null) {
      throw new NotifiableException("Pool not found: " + poolId);
    }

    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_STARTED, 1);
    if (log.isDebugEnabled()) {
      log.info(
          " • Connecting client to pool: "
              + whirlpoolUtxo.getUtxoConfig().getPoolId()
              + ", utxo="
              + whirlpoolUtxo
              + " ; "
              + whirlpoolUtxo.getUtxoConfig());
    } else {
      log.info(" • Connecting client to pool: " + whirlpoolUtxo.getUtxoConfig().getPoolId());
    }

    WhirlpoolClientListener loggingListener = new LoggingWhirlpoolClientListener(notifyListener);
    WhirlpoolClientListener listener =
        new UtxoWhirlpoolClientListener(loggingListener, whirlpoolUtxo, this);

    // start mixing (whirlpoolClient will start a new thread)
    MixParams mixParams = computeMixParams(whirlpoolUtxo, pool);
    final WhirlpoolClient mixClient = config.newClient();
    mixClient.whirlpool(mixParams, listener);

    return mixClient;
  }

  public void onMixSuccess(MixSuccess mixSuccess, WhirlpoolUtxo whirlpoolUtxo) {
    // preserve utxo config
    Utxo receiveUtxo = mixSuccess.getReceiveUtxo();
    setUtxoConfig(
        whirlpoolUtxo.getUtxoConfig().copy(), receiveUtxo.getHash(), (int) receiveUtxo.getIndex());
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

  private MixParams computeMixParams(WhirlpoolUtxo whirlpoolUtxo, Pool pool) {
    IPremixHandler premixHandler = computePremixHandler(whirlpoolUtxo);
    IPostmixHandler postmixHandler = computePostmixHandler();
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
    if (accounts.length == 0) {
      accounts = WhirlpoolAccount.values();
    }
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

  private void setUtxoConfig(WhirlpoolUtxoConfig utxoConfig, String txid) {
    walletPersistHandler.setUtxoConfig(txid, utxoConfig);
  }

  public void setUtxoConfig(WhirlpoolUtxoConfig utxoConfig, String utxoHash, int utxoIndex) {
    walletPersistHandler.setUtxoConfig(utxoHash, utxoIndex, utxoConfig);
  }

  private WhirlpoolUtxoConfig getUtxoConfigOrNull(UnspentOutput utxo) {
    // search by utxo
    WhirlpoolUtxoConfig utxoConfig =
        walletPersistHandler.getUtxoConfig(utxo.tx_hash, utxo.tx_output_n);
    return utxoConfig; // null if not found
  }

  public WhirlpoolUtxoConfig getUtxoConfig(WhirlpoolUtxo whirlpoolUtxo) {
    UnspentOutput utxo = whirlpoolUtxo.getUtxo();

    // search by utxo
    WhirlpoolUtxoConfig utxoConfig = getUtxoConfigOrNull(utxo);
    if (utxoConfig != null) {
      return utxoConfig;
    }

    // default value
    utxoConfig = new WhirlpoolUtxoConfig(this, config.getMixsTarget());
    if (WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())) {
      // POSTMIX was already mixed once (at least)
      utxoConfig.incrementMixsDone();
    }
    setUtxoConfig(utxoConfig, utxo.tx_hash, utxo.tx_output_n);
    return utxoConfig;
  }

  public WhirlpoolWalletPersistHandler getWalletPersistHandler() {
    return walletPersistHandler;
  }

  protected void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo) {

    // preserve utxo config
    UnspentOutput utxo = whirlpoolUtxo.getUtxo();
    // find by utxo (new POSTMIX from mix or CLI restart)
    WhirlpoolUtxoConfig utxoConfig = getUtxoConfigOrNull(utxo);
    if (utxoConfig != null) {
      // utxoConfig found (from previous mix)
      if (log.isDebugEnabled()) {
        log.debug("New utxo detected: " + whirlpoolUtxo + " ; (existing utxoConfig) " + utxoConfig);
      }
    } else {
      // find by tx hash (new PREMIX from TX0)
      utxoConfig = walletPersistHandler.getUtxoConfig(utxo.tx_hash);
      if (utxoConfig != null) {
        utxoConfig = new WhirlpoolUtxoConfig(utxoConfig);
        setUtxoConfig(utxoConfig, utxo.tx_hash, utxo.tx_output_n);
        if (log.isDebugEnabled()) {
          log.debug("New utxo detected: " + whirlpoolUtxo + " ; (from TX0) " + utxoConfig);
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("New utxo detected: " + whirlpoolUtxo + " (no utxoConfig)");
        }
      }
    }
    if (utxoConfig != null && utxoConfig.getPoolId() != null) {
      // check configured pool exists
      Pool pool = null;
      try {
        pool = findPoolById(utxoConfig.getPoolId());
      } catch (Exception e) {
        log.error("", e);
      }
      if (pool == null) {
        // clear pool configuration
        log.warn(
            "pool not found for utxoConfig: "
                + utxoConfig.getPoolId()
                + " => reset utxoConfig.poolId");
        utxoConfig.setPoolId(null);
      }
    }

    // auto-assign pool when possible
    if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
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
      eligiblePools = findPoolsForTx0(whirlpoolUtxo.getUtxo().value, 1, Tx0FeeTarget.DEFAULT);
    }

    // find eligible pools for mix
    else if (WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())
        || WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())) {
      boolean liquidity = WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount());
      eligiblePools = findPoolsForPremix(whirlpoolUtxo.getUtxo().value, liquidity);
    }

    // auto-assign pool by preference when found
    if (eligiblePools != null) {
      if (!eligiblePools.isEmpty()) {
        String poolId = eligiblePools.iterator().next().getPoolId();
        if (log.isDebugEnabled()) {
          log.debug("autoAssignPool: " + whirlpoolUtxo + " -> " + poolId);
        }
        whirlpoolUtxo.getUtxoConfig().setPoolId(poolId);
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

    if (oldConfirmations == 0 && freshConfirmations > 0) {
      if (log.isDebugEnabled()) {
        log.debug("New utxo CONFIRMED: " + whirlpoolUtxo + " ; " + whirlpoolUtxo.getUtxoConfig());
      }
    }

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
    notifyError(message);
  }

  public void notifyError(String message) {
    log.error(message);
  }

  public boolean hasMoreMixableOrUnconfirmed() {
    return mixOrchestrator.hasMoreMixableOrUnconfirmed();
  }

  public String getZpubDeposit() {
    return depositWallet.getZpub();
  }

  public String getZpubPremix() {
    return premixWallet.getZpub();
  }

  public String getZpubPostmix() {
    return postmixWallet.getZpub();
  }

  public void onUtxoConfigChanged(WhirlpoolUtxoConfig whirlpoolUtxoConfig) {
    getWalletPersistHandler().onUtxoConfigChanged(whirlpoolUtxoConfig);
  }
}
