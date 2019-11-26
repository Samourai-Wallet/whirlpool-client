package com.samourai.whirlpool.client.wallet;

import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.*;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Config;
import com.samourai.whirlpool.client.tx0.UnspentOutputWithKey;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoMixOrchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoTx0Orchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.MixOrchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.PersistOrchestrator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWallet {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWallet.class);
  public static final int MIX_MIN_CONFIRMATIONS = 1;

  private WhirlpoolWalletConfig config;
  private WhirlpoolDataService dataService;

  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;
  private Bip84ApiWallet badbankWallet;

  private PersistOrchestrator persistOrchestrator;
  protected MixOrchestrator mixOrchestrator;
  private Optional<AutoTx0Orchestrator> autoTx0Orchestrator;
  private Optional<AutoMixOrchestrator> autoMixOrchestrator;

  protected WhirlpoolWallet(WhirlpoolWallet whirlpoolWallet) {
    this(
        whirlpoolWallet.config,
        whirlpoolWallet.dataService,
        whirlpoolWallet.bech32Util,
        whirlpoolWallet.whirlpoolClient,
        whirlpoolWallet.depositWallet,
        whirlpoolWallet.premixWallet,
        whirlpoolWallet.postmixWallet,
        whirlpoolWallet.badbankWallet);
  }

  public WhirlpoolWallet(
      WhirlpoolWalletConfig config,
      WhirlpoolDataService dataService,
      Bech32UtilGeneric bech32Util,
      WhirlpoolClient whirlpoolClient,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet,
      Bip84ApiWallet badbankWallet) {
    this.config = config;
    this.dataService = dataService;

    this.bech32Util = bech32Util;

    this.whirlpoolClient = whirlpoolClient;

    this.depositWallet = depositWallet;
    this.premixWallet = premixWallet;
    this.postmixWallet = postmixWallet;
    this.badbankWallet = badbankWallet;

    this.persistOrchestrator =
        new PersistOrchestrator(
            config.getPersistDelay() * 1000, this, config.getPersistCleanDelay() * 1000);
    int loopDelay = config.getRefreshUtxoDelay() * 1000;
    this.mixOrchestrator =
        new MixOrchestrator(
            loopDelay,
            this,
            config.getMaxClients(),
            config.getMaxClientsPerPool(),
            config.getClientDelay());

    if (config.isAutoTx0()) {
      this.autoTx0Orchestrator =
          Optional.of(
              new AutoTx0Orchestrator(
                  loopDelay, this, config.getTx0Delay(), config.getAutoTx0PoolId()));
    } else {
      this.autoTx0Orchestrator = Optional.empty();
    }
    if (config.isAutoMix()) {
      this.autoMixOrchestrator = Optional.of(new AutoMixOrchestrator(loopDelay, this));
    } else {
      this.autoMixOrchestrator = Optional.empty();
    }

    this.clearCache();

    this.config.getPersistHandler().loadUtxoConfigs(this);
  }

  public void clearCache() {
    dataService.clear();
  }

  public void clearCache(WhirlpoolAccount account) {
    dataService.clearUtxos(account);
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
      dataService.clearPools();
    }

    // find eligible pools
    Collection<Pool> pools = getPools();
    int fee = getFee(feeTarget);
    return config.getTx0Service().findPools(nbOutputsMin, pools, utxoValue, fee, getFeePremix());
  }

  private boolean isPoolApplicable(Pool pool, WhirlpoolUtxo whirlpoolUtxo) {
    long utxoValue = whirlpoolUtxo.getUtxo().value;
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())) {
      long tx0BalanceMin = computeTx0SpendFromBalanceMin(pool, Tx0FeeTarget.MIN, 1);
      return utxoValue >= tx0BalanceMin;
    }
    if (WhirlpoolAccount.PREMIX.equals(whirlpoolUtxo.getAccount())) {
      return pool.checkInputBalance(utxoValue, false);
    }
    if (WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())) {
      return utxoValue == pool.getDenomination();
    }
    log.error("Unknown account for whirlpoolUtxo:" + whirlpoolUtxo);
    return false;
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
            .sorted(getUtxoComparator())
            .collect(Collectors.<WhirlpoolUtxo>toList());

    int feeTx0 = getFee(feeTarget);

    return findTx0SpendFrom(
        nbOutputsMin,
        pool,
        depositUtxosByPriority,
        feeTx0,
        feePremix); // throws EmptyWalletException, UnconfirmedUtxoException
  }

  public WhirlpoolUtxoPriorityComparator getUtxoComparator() {
    return mixOrchestrator.computeWhirlpoolUtxoPriorityComparator();
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
      Collection<Pool> eligiblePools =
          config
              .getTx0Service()
              .findPools(
                  nbOutputsMin, Lists.of(pool), whirlpoolUtxo.getUtxo().value, feeTx0, feePremix);
      // check pool
      if (!eligiblePools.isEmpty()) {

        // check confirmation
        if (whirlpoolUtxo.getUtxo().confirmations >= config.getTx0MinConfirmations()) {

          // set pool
          whirlpoolUtxo.getUtxoConfig().setPoolId(pool.getPoolId());

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
        config.getTx0Service().computeSpendFromBalanceMin(pool, feeTx0, feePremix, nbOutputsMin);
    throw new EmptyWalletException("No UTXO found to spend TX0 from", requiredBalance);
  }

  public long computeTx0SpendFromBalanceMin(Pool pool, Tx0FeeTarget feeTarget, int nbPremix) {
    int feeTx0 = getFee(feeTarget);
    return config
        .getTx0Service()
        .computeSpendFromBalanceMin(pool, feeTx0, getFeePremix(), nbPremix);
  }

  public Tx0 autoTx0() throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    String poolId = config.getAutoTx0PoolId();
    Tx0FeeTarget feeTarget = config.getAutoTx0FeeTarget();
    Pool pool = findPoolById(poolId);
    if (pool == null) {
      throw new NotifiableException(
          "No pool found for autoTx0 (autoTx0 = " + (poolId != null ? poolId : "null") + ")");
    }

    WhirlpoolUtxo spendFrom =
        findTx0SpendFrom(
            1,
            pool,
            feeTarget,
            getFeePremix()); // throws UnconfirmedUtxoException, EmptyWalletException

    return tx0(spendFrom, getTx0Config(), feeTarget);
  }

  public Tx0 tx0(WhirlpoolUtxo whirlpoolUtxoSpendFrom, Tx0Config tx0Config, Tx0FeeTarget feeTarget)
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
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_FAILED, true, 0);
      whirlpoolUtxoSpendFrom.setError("Tx0 failed: no pool set");
      throw new NotifiableException("Tx0 failed: no pool set");
    }

    UnspentOutput utxoSpendFrom = whirlpoolUtxoSpendFrom.getUtxo();

    whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0, true, 50);
    try {
      byte[] spendFromPrivKey =
          depositWallet.getAddressAt(utxoSpendFrom).getECKey().getPrivKeyBytes();

      int feeTx0 = getFee(feeTarget);
      if (log.isDebugEnabled()) {
        log.debug("Tx0 fee: feeTarget=" + feeTarget + " => " + feeTx0);
      }

      UnspentOutputWithKey spendFrom = new UnspentOutputWithKey(utxoSpendFrom, spendFromPrivKey);
      Collection<UnspentOutputWithKey> spendFroms = Lists.of(spendFrom);
      Tx0 tx0 = tx0(spendFroms, pool, tx0Config, feeTx0);

      // success
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_SUCCESS, true, 100);
      whirlpoolUtxoSpendFrom.setMessage("TX0 txid: " + tx0.getTx().getHashAsString());

      // preserve utxo config
      String tx0Txid = tx0.getTx().getHashAsString();
      setUtxoConfig(whirlpoolUtxoSpendFrom.getUtxoConfig().copy(), tx0Txid);

      return tx0;
    } catch (Exception e) {
      // error
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TX0_FAILED, true, 100);
      whirlpoolUtxoSpendFrom.setError(e);
      throw e;
    }
  }

  public Tx0 tx0(
      Collection<UnspentOutputWithKey> spendFroms,
      Pool pool,
      Tx0Config tx0Config,
      Tx0FeeTarget feeTarget)
      throws Exception {
    int feeTx0 = getFee(feeTarget);
    return tx0(spendFroms, pool, tx0Config, feeTx0);
  }

  public Tx0 tx0(
      Collection<UnspentOutputWithKey> spendFroms, Pool pool, Tx0Config tx0Config, int feeTx0)
      throws Exception {

    // check confirmations
    for (UnspentOutputWithKey spendFrom : spendFroms) {
      if (spendFrom.confirmations < config.getTx0MinConfirmations()) {
        log.error("Minimum confirmation(s) for tx0: " + config.getTx0MinConfirmations());
        throw new UnconfirmedUtxoException(spendFrom);
      }
    }

    // run tx0
    int initialPremixIndex = premixWallet.getIndexHandler().get();
    try {
      Tx0 tx0 =
          config
              .getTx0Service()
              .tx0(
                  spendFroms,
                  depositWallet,
                  premixWallet,
                  badbankWallet,
                  tx0Config,
                  feeTx0,
                  getFeePremix(),
                  pool);

      log.info(
          " • Tx0 result: txid="
              + tx0.getTx().getHashAsString()
              + ", nbPremixs="
              + tx0.getPremixOutputs().size());
      if (log.isDebugEnabled()) {
        log.debug(tx0.getTx().toString());
      }

      // pushTx
      try {
        config.getBackendApi().pushTx(ClientUtils.getTxHex(tx0.getTx()));
      } catch (Exception e) {
        // preserve pushTx message
        throw new NotifiableException(e.getMessage());
      }

      // refresh utxos
      ClientUtils.sleepRefreshUtxos(config.getNetworkParameters());
      clearCache(WhirlpoolAccount.DEPOSIT);
      clearCache(WhirlpoolAccount.PREMIX);
      return tx0;
    } catch (Exception e) {
      // revert index
      premixWallet.getIndexHandler().set(initialPremixIndex);
      throw e;
    }
  }

  public Tx0Config getTx0Config() {
    Tx0Config tx0Config = new Tx0Config();
    tx0Config.setMaxOutputs(config.getTx0MaxOutputs());
    tx0Config.setBadbankChange(false);
    return tx0Config;
  }

  public synchronized void start() {
    if (isStarted()) {
      log.warn("NOT starting WhirlpoolWallet: already started");
      return;
    }
    log.info(" • Starting WhirlpoolWallet");

    // reset utxos
    clearCache();

    // fetch utxos before starting orchestrators to fix concurrency issue on startup
    // (lock between findAndMix() -> recursive call to utxosupplier[account].get()
    try {
      getUtxos(false);
    } catch (Exception e) {
      log.error("", e);
    }

    persistOrchestrator.start();
    this.mixOrchestrator.start();
    if (this.autoTx0Orchestrator.isPresent()) {
      this.autoTx0Orchestrator.get().start();
    }
    if (this.autoMixOrchestrator.isPresent()) {
      this.autoMixOrchestrator.get().start();
    }
  }

  public synchronized void stop() {
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
    // check pool
    Pool pool = null;
    if (poolId != null) {
      // check pool exists
      pool = findPoolById(poolId);
      if (pool == null) {
        throw new NotifiableException("Pool not found: " + poolId);
      }

      // check pool applicable
      if (!isPoolApplicable(pool, whirlpoolUtxo)) {
        throw new NotifiableException("Pool not applicable for utxo: " + poolId);
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
    return getFee(feeTarget.getFeeTarget());
  }

  public int getFee(MinerFeeTarget feeTarget) {
    return dataService.getFeeSatPerByte(feeTarget);
  }

  public int getFeePremix() {
    return dataService.getFeeSatPerByte(config.getFeeTargetPremix());
  }

  protected Pools getPoolsResponse() throws Exception {
    return getPoolsResponse(false);
  }

  protected Pools getPoolsResponse(boolean clearCache) throws Exception {
    if (clearCache) {
      dataService.clearPools();
    }
    return dataService.getPoolsResponse();
  }

  public Collection<Pool> getPools() throws Exception {
    return getPools(false);
  }

  public Collection<Pool> getPools(boolean clearCache) throws Exception {
    if (clearCache) {
      dataService.clearPools();
    }
    return dataService.getPools();
  }

  public Pool findPoolById(String poolId) throws Exception {
    if (poolId == null) {
      return null;
    }
    return findPoolById(poolId, false);
  }

  public Pool findPoolById(String poolId, boolean clearCache) throws Exception {
    for (Pool pool : getPools(clearCache)) {
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
      dataService.clearPools();
    }

    // find eligible pools
    List<Pool> poolsAccepted = new ArrayList<Pool>();
    for (Pool pool : getPools()) {
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

  public Bip84ApiWallet getBadbankWallet() {
    return badbankWallet;
  }

  protected Bip84ApiWallet getWallet(WhirlpoolAccount account) {
    switch (account) {
      case DEPOSIT:
        return depositWallet;
      case PREMIX:
        return premixWallet;
      case POSTMIX:
        return postmixWallet;
      case BADBANK:
        return badbankWallet;
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

    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_STARTED, true, 1);
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

  public void onMixSuccess(WhirlpoolUtxo whirlpoolUtxo, MixSuccess mixSuccess) {
    // preserve utxo config
    Utxo receiveUtxo = mixSuccess.getReceiveUtxo();
    setUtxoConfig(
        whirlpoolUtxo.getUtxoConfig().copy(), receiveUtxo.getHash(), (int) receiveUtxo.getIndex());
  }

  public void onMixFail(WhirlpoolUtxo whirlpoolUtxo, MixFailReason reason, String notifiableError) {
    // is utxo still mixable?
    if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
      // utxo was spent in the meantime
      return;
    }

    // retry
    try {
      mixQueue(whirlpoolUtxo);
    } catch (Exception e) {
      log.error("", e);
    }
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
    return dataService.findUtxo(utxoHash, utxoIndex, WhirlpoolAccount.values());
  }

  public WhirlpoolUtxo findUtxo(String utxoHash, int utxoIndex, WhirlpoolAccount... accounts)
      throws Exception {
    return dataService.findUtxo(utxoHash, utxoIndex, accounts);
  }

  public Collection<WhirlpoolUtxo> getUtxos(boolean clearCache, WhirlpoolAccount... accounts)
      throws Exception {
    if (accounts.length == 0) {
      accounts = WhirlpoolAccount.values();
    }
    return dataService.getUtxos(clearCache, accounts);
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
    config.getPersistHandler().setUtxoConfig(txid, utxoConfig);
  }

  public void setUtxoConfig(WhirlpoolUtxoConfig utxoConfig, String utxoHash, int utxoIndex) {
    config.getPersistHandler().setUtxoConfig(utxoHash, utxoIndex, utxoConfig);
  }

  private WhirlpoolUtxoConfig getUtxoConfigOrNull(UnspentOutput utxo) {
    // search by utxo
    WhirlpoolUtxoConfig utxoConfig =
        config.getPersistHandler().getUtxoConfig(utxo.tx_hash, utxo.tx_output_n);
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
    if (log.isDebugEnabled()) {
      log.debug("New default UtxoConfig for utxo: " + whirlpoolUtxo);
    }
    utxoConfig = new WhirlpoolUtxoConfig(this, config.getMixsTarget());
    if (WhirlpoolAccount.POSTMIX.equals(whirlpoolUtxo.getAccount())) {
      // POSTMIX was already mixed once (at least)
      utxoConfig.incrementMixsDone();
    }
    setUtxoConfig(utxoConfig, utxo.tx_hash, utxo.tx_output_n);
    return utxoConfig;
  }

  protected void onUtxoDetected(WhirlpoolUtxo whirlpoolUtxo, boolean isFirstFetch) {
    String firstFetchInfo = isFirstFetch ? "(init) " : "";

    // preserve utxo config
    UnspentOutput utxo = whirlpoolUtxo.getUtxo();

    // find by utxo (new POSTMIX from mix or CLI restart)
    WhirlpoolUtxoConfig utxoConfig = getUtxoConfigOrNull(utxo);
    if (utxoConfig != null) {
      // utxoConfig found (from previous mix)
      if (log.isDebugEnabled()) {
        log.debug(
            firstFetchInfo
                + "New utxo detected: "
                + whirlpoolUtxo
                + " ; (existing utxoConfig) "
                + utxoConfig);
      }
    } else {
      // find by tx hash (new PREMIX from TX0)
      utxoConfig = config.getPersistHandler().getUtxoConfig(utxo.tx_hash);
      if (utxoConfig != null) {
        utxoConfig = new WhirlpoolUtxoConfig(utxoConfig);
        setUtxoConfig(utxoConfig, utxo.tx_hash, utxo.tx_output_n);
        if (log.isDebugEnabled()) {
          log.debug(
              firstFetchInfo
                  + "New utxo detected: "
                  + whirlpoolUtxo
                  + " ; (from TX0) "
                  + utxoConfig);
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug(firstFetchInfo + "New utxo detected: " + whirlpoolUtxo + " (no utxoConfig)");
        }
      }
    }
    if (utxoConfig != null && utxoConfig.getPoolId() != null) {
      // check configured pool is valid
      Pool pool = null;
      try {
        // check pool exists
        pool = findPoolById(utxoConfig.getPoolId());

        // check pool is applicable
        if (pool != null && !isPoolApplicable(pool, whirlpoolUtxo)) {
          if (log.isDebugEnabled()) {
            log.debug(
                firstFetchInfo + "pool not applicable for utxo value: " + utxoConfig.getPoolId());
          }
          pool = null;
        }

      } catch (Exception e) {
        log.error("", e);
      }
      if (pool == null) {
        // clear pool configuration
        log.warn(
            firstFetchInfo
                + "pool not found for utxoConfig: "
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
    mixOrchestrator.onUtxoDetected(whirlpoolUtxo, isFirstFetch);
    if (autoTx0Orchestrator.isPresent()) {
      autoTx0Orchestrator.get().onUtxoDetected(whirlpoolUtxo, isFirstFetch);
    }
    if (autoMixOrchestrator.isPresent()) {
      autoMixOrchestrator.get().onUtxoDetected(whirlpoolUtxo, isFirstFetch);
    }
  }

  private void autoAssignPool(WhirlpoolUtxo whirlpoolUtxo) throws Exception {
    Collection<Pool> eligiblePools = null;

    // find eligible pools for tx0
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())) {
      eligiblePools = findPoolsForTx0(whirlpoolUtxo.getUtxo().value, 1, Tx0FeeTarget.MIN);
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
        && wasConfirmed(config.getTx0MinConfirmations(), oldConfirmations, freshConfirmations)) {
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

  public boolean hasMoreMixingThreadAvailable(String poolId) {
    return mixOrchestrator.hasMoreMixingThreadAvailable(poolId);
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

  public String getZpubBadBank() {
    return badbankWallet.getZpub();
  }

  public void onUtxoConfigChanged(WhirlpoolUtxoConfig whirlpoolUtxoConfig) {
    config.getPersistHandler().onUtxoConfigChanged(whirlpoolUtxoConfig);
  }

  public WhirlpoolWalletConfig getConfig() {
    return config;
  }
}
