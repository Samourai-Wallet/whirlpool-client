package com.samourai.whirlpool.client.wallet;

import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.tx0.*;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.orchestrator.AutoTx0Orchestrator;
import com.samourai.whirlpool.client.wallet.orchestrator.PersistOrchestrator;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.protocol.beans.Utxo;
import io.reactivex.Observable;
import java.util.*;
import java8.util.Lists;
import java8.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWallet {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWallet.class);

  private WhirlpoolWalletConfig config;
  private WhirlpoolDataService dataService;

  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;
  private Bip84ApiWallet badbankWallet;

  private PersistOrchestrator persistOrchestrator;
  protected MixOrchestratorImpl mixOrchestrator;
  private Optional<AutoTx0Orchestrator> autoTx0Orchestrator;

  private MixingStateEditable mixingState;

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

    this.mixingState = new MixingStateEditable(false);

    this.persistOrchestrator =
        new PersistOrchestrator(
            config.getPersistDelay() * 1000, this, config.getPersistCleanDelay() * 1000);
    int loopDelay = config.getRefreshUtxoDelay() * 1000;
    this.mixOrchestrator = new MixOrchestratorImpl(mixingState, loopDelay, this);

    if (config.isAutoTx0()) {
      this.autoTx0Orchestrator =
          Optional.of(
              new AutoTx0Orchestrator(
                  loopDelay, this, config.getTx0Delay(), config.getAutoTx0PoolId()));
    } else {
      this.autoTx0Orchestrator = Optional.empty();
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

  protected Collection<Pool> findPoolsForTx0(
      Tx0ParamSimple tx0ParamSimple, long utxoValue, int nbOutputsMin, boolean clearCache)
      throws Exception {
    // clear cache
    if (clearCache) {
      dataService.clearPools();
    }

    // find eligible pools
    Collection<Pool> pools = getPools();
    return config.getTx0Service().findPools(tx0ParamSimple, nbOutputsMin, pools, utxoValue);
  }

  private boolean isPoolApplicable(Pool pool, WhirlpoolUtxo whirlpoolUtxo, Long overspendOrNull) {
    long utxoValue = whirlpoolUtxo.getUtxo().value;
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())) {
      Tx0Param tx0Param = getTx0Param(pool, Tx0FeeTarget.MIN, overspendOrNull);
      long tx0BalanceMin = config.getTx0Service().computeSpendFromBalanceMin(tx0Param, 1);
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

  private WhirlpoolUtxo findTx0SpendFrom(Tx0Param tx0Param, int nbOutputsMin)
      throws Exception { // throws EmptyWalletException, UnconfirmedUtxoException

    List<WhirlpoolUtxo> depositUtxosByPriority =
        new LinkedList<WhirlpoolUtxo>(getUtxosDeposit(true));
    Collections.shuffle(depositUtxosByPriority);
    return findTx0SpendFrom(
        tx0Param,
        nbOutputsMin,
        depositUtxosByPriority); // throws EmptyWalletException, UnconfirmedUtxoException
  }

  private WhirlpoolUtxo findTx0SpendFrom(
      Tx0Param tx0Param, int nbOutputsMin, Collection<WhirlpoolUtxo> depositUtxosByPriority)
      throws EmptyWalletException, Exception, NotifiableException {
    Pool pool = tx0Param.getPool();

    WhirlpoolUtxo unconfirmedUtxo = null;
    for (WhirlpoolUtxo whirlpoolUtxo : depositUtxosByPriority) {
      Collection<Pool> eligiblePools =
          config
              .getTx0Service()
              .findPools(tx0Param, nbOutputsMin, Lists.of(pool), whirlpoolUtxo.getUtxo().value);
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
        config.getTx0Service().computeSpendFromBalanceMin(tx0Param, nbOutputsMin);
    throw new EmptyWalletException("No UTXO found to spend TX0 from", requiredBalance);
  }

  public long computeTx0SpendFromBalanceMin(
      Pool pool, Tx0FeeTarget tx0FeeTarget, int nbPremix, Long overspendOrNull) {
    Tx0Param tx0Param = getTx0Param(pool, tx0FeeTarget, overspendOrNull);
    return config.getTx0Service().computeSpendFromBalanceMin(tx0Param, nbPremix);
  }

  protected Tx0Param getTx0Param(Pool pool, Tx0FeeTarget tx0FeeTarget, Long overspendOrNull) {
    int feeTx0 = getFee(tx0FeeTarget);
    int feePremix = getFeePremix();
    Tx0Param tx0Param = new Tx0Param(feeTx0, feePremix, pool, overspendOrNull);
    return tx0Param;
  }

  protected Tx0ParamSimple getTx0ParamSimple(Tx0FeeTarget tx0FeeTarget) {
    int feeTx0 = getFee(tx0FeeTarget);
    int feePremix = getFeePremix();
    Tx0ParamSimple tx0ParamSimple = new Tx0ParamSimple(feeTx0, feePremix);
    return tx0ParamSimple;
  }

  public Tx0 autoTx0() throws Exception { // throws UnconfirmedUtxoException, EmptyWalletException
    String poolId = config.getAutoTx0PoolId();
    Pool pool = findPoolById(poolId);
    if (pool == null) {
      throw new NotifiableException(
          "No pool found for autoTx0 (autoTx0 = " + (poolId != null ? poolId : "null") + ")");
    }
    Tx0FeeTarget tx0FeeTarget = config.getAutoTx0FeeTarget();
    Tx0Param tx0Param = getTx0Param(pool, tx0FeeTarget, null);

    Tx0Config tx0Config = getTx0Config(pool);
    WhirlpoolUtxo spendFrom =
        findTx0SpendFrom(tx0Param, 1); // throws UnconfirmedUtxoException, EmptyWalletException

    return tx0(Lists.of(spendFrom), pool, tx0FeeTarget, tx0Config);
  }

  public Tx0Preview tx0Preview(
      Collection<WhirlpoolUtxo> whirlpoolUtxos,
      Pool pool,
      Tx0Config tx0Config,
      Tx0FeeTarget feeTarget)
      throws Exception {

    Collection<UnspentOutputWithKey> utxos = toUnspentOutputWithKeys(whirlpoolUtxos);
    return tx0Preview(pool, utxos, tx0Config, feeTarget);
  }

  public Tx0Preview tx0Preview(
      Pool pool,
      Collection<UnspentOutputWithKey> spendFroms,
      Tx0Config tx0Config,
      Tx0FeeTarget tx0FeeTarget)
      throws Exception {

    Tx0Param tx0Param = getTx0Param(pool, tx0FeeTarget, tx0Config.getOverspend());
    return config.getTx0Service().tx0Preview(spendFroms, tx0Config, tx0Param);
  }

  public Tx0 tx0(
      Collection<WhirlpoolUtxo> whirlpoolUtxos,
      Pool pool,
      Tx0FeeTarget feeTarget,
      Tx0Config tx0Config)
      throws Exception {

    Collection<UnspentOutputWithKey> spendFroms = toUnspentOutputWithKeys(whirlpoolUtxos);

    // verify utxos
    String poolId = pool.getPoolId();
    for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
      // check status
      WhirlpoolUtxoStatus utxoStatus = whirlpoolUtxo.getUtxoState().getStatus();
      if (!WhirlpoolUtxoStatus.READY.equals(utxoStatus)
          && !WhirlpoolUtxoStatus.STOP.equals(utxoStatus)
          && !WhirlpoolUtxoStatus.TX0_FAILED.equals(utxoStatus)) {
        throw new NotifiableException("Cannot Tx0: utxoStatus=" + utxoStatus);
      }
    }

    // set utxos
    for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
      // set pool
      if (!poolId.equals(whirlpoolUtxo.getUtxoConfig().getPoolId())) {
        whirlpoolUtxo.getUtxoConfig().setPoolId(poolId);
      }
      // set status
      whirlpoolUtxo.getUtxoState().setStatus(WhirlpoolUtxoStatus.TX0, true);
    }
    try {
      // run
      Tx0 tx0 = tx0(spendFroms, pool, tx0Config, feeTarget);

      // success
      for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
        WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
        utxoState.setStatus(WhirlpoolUtxoStatus.TX0_SUCCESS, true);
      }

      // preserve utxo config
      String tx0Txid = tx0.getTx().getHashAsString();
      addUtxoConfig(whirlpoolUtxos.iterator().next().getUtxoConfig().copy(), tx0Txid);

      return tx0;
    } catch (Exception e) {
      // error
      for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
        WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
        utxoState.setStatus(WhirlpoolUtxoStatus.TX0_FAILED, true);
        utxoState.setError(e);
      }
      throw e;
    }
  }

  public Tx0 tx0(
      Collection<UnspentOutputWithKey> spendFroms,
      Pool pool,
      Tx0Config tx0Config,
      Tx0FeeTarget tx0FeeTarget)
      throws Exception {

    // check confirmations
    for (UnspentOutputWithKey spendFrom : spendFroms) {
      if (spendFrom.confirmations < config.getTx0MinConfirmations()) {
        log.error("Minimum confirmation(s) for tx0: " + config.getTx0MinConfirmations());
        throw new UnconfirmedUtxoException(spendFrom);
      }
    }

    Tx0Param tx0Param = getTx0Param(pool, tx0FeeTarget, tx0Config.getOverspend());

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
                  postmixWallet,
                  badbankWallet,
                  tx0Config,
                  tx0Param);

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

  private Collection<UnspentOutputWithKey> toUnspentOutputWithKeys(
      Collection<WhirlpoolUtxo> whirlpoolUtxos) {
    Collection<UnspentOutputWithKey> spendFroms = new LinkedList<UnspentOutputWithKey>();

    for (WhirlpoolUtxo whirlpoolUtxo : whirlpoolUtxos) {
      UnspentOutput utxo = whirlpoolUtxo.getUtxo();
      byte[] utxoKey = depositWallet.getAddressAt(utxo).getECKey().getPrivKeyBytes();
      UnspentOutputWithKey spendFrom = new UnspentOutputWithKey(utxo, utxoKey);
      spendFroms.add(spendFrom);
    }
    return spendFroms;
  }

  public Tx0Config getTx0Config(Pool pool) { // pool arg used by CLI override
    Tx0Config tx0Config = new Tx0Config();
    return tx0Config;
  }

  public synchronized void start() {
    if (mixingState.isStarted()) {
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

    persistOrchestrator.start(true);
    this.mixOrchestrator.start(true);
    if (this.autoTx0Orchestrator.isPresent()) {
      this.autoTx0Orchestrator.get().start(true);
    }
    mixingState.setStarted(true);
  }

  public synchronized void stop() {
    if (!mixingState.isStarted()) {
      log.warn("NOT stopping WhirlpoolWallet: not started");
      return;
    }
    log.info(" • Stopping WhirlpoolWallet");
    this.mixOrchestrator.stop();
    if (this.autoTx0Orchestrator.isPresent()) {
      this.autoTx0Orchestrator.get().stop();
    }
    persistOrchestrator.stop();

    mixingState.setStarted(false);

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
      if (!isPoolApplicable(pool, whirlpoolUtxo, null)) {
        throw new NotifiableException("Pool not applicable for utxo: " + poolId);
      }
      poolId = pool.getPoolId();
    }
    // set pool
    whirlpoolUtxo.getUtxoConfig().setPoolId(poolId);
  }

  public void setMixsTarget(WhirlpoolUtxo whirlpoolUtxo, Integer mixsTarget)
      throws NotifiableException {
    if (mixsTarget != null && mixsTarget < 0) {
      throw new NotifiableException("Invalid mixsTarget: " + mixsTarget);
    }
    whirlpoolUtxo.getUtxoConfig().setMixsTarget(mixsTarget);
  }

  public void mixQueue(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    this.mixOrchestrator.mixQueue(whirlpoolUtxo);
  }

  public void mixStop(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    this.mixOrchestrator.mixStop(whirlpoolUtxo, true, false);
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

  public Observable<MixProgress> mix(WhirlpoolUtxo whirlpoolUtxo) throws NotifiableException {
    return mixOrchestrator.mixNow(whirlpoolUtxo);
  }

  public void onMixSuccess(WhirlpoolUtxo whirlpoolUtxo, MixSuccess mixSuccess) {
    // preserve utxo config
    Utxo receiveUtxo = mixSuccess.getReceiveUtxo();
    addUtxoConfig(
        whirlpoolUtxo.getUtxoConfig().copy(), receiveUtxo.getHash(), (int) receiveUtxo.getIndex());

    // refresh utxos
    clearCache(whirlpoolUtxo.getAccount());
    clearCache(WhirlpoolAccount.POSTMIX);
  }

  public void onMixFail(WhirlpoolUtxo whirlpoolUtxo, MixFailReason reason, String notifiableError) {
    switch (reason) {
      case PROTOCOL_MISMATCH:
        // stop mixing on protocol mismatch
        log.error("onMixFail(" + reason + "): stopping mixing");
        stop();
        break;

      case DISCONNECTED:
      case MIX_FAILED:
        // is utxo still mixable?
        if (whirlpoolUtxo.getUtxoConfig().getPoolId() == null) {
          // utxo was spent in the meantime
          log.warn(
              "onMixFail(" + reason + "): not retrying because UTXO was spent: " + whirlpoolUtxo);
          return;
        }

        // retry later
        log.info("onMixFail(" + reason + "): will retry later");
        try {
          mixQueue(whirlpoolUtxo);
        } catch (Exception e) {
          log.error("", e);
        }
        break;

      case INPUT_REJECTED:
      case INTERNAL_ERROR:
      case STOP:
      case CANCEL:
        // not retrying
        log.warn("onMixFail(" + reason + "): won't retry");
        break;

      default:
        // not retrying
        log.warn("onMixFail(" + reason + "): unknown reason");
        break;
    }
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

  public MixingState getMixingState() {
    return mixingState;
  }

  public String getDepositAddress(boolean increment) {
    return bech32Util.toBech32(
        depositWallet.getNextAddress(increment), config.getNetworkParameters());
  }

  private void addUtxoConfig(WhirlpoolUtxoConfig utxoConfig, String txid) {
    config.getPersistHandler().addUtxoConfig(txid, utxoConfig);
  }

  private void addUtxoConfig(WhirlpoolUtxoConfig utxoConfig, String utxoHash, int utxoIndex) {
    config.getPersistHandler().addUtxoConfig(utxoHash, utxoIndex, utxoConfig);
  }

  private WhirlpoolUtxoConfig getUtxoConfigOrNull(UnspentOutput utxo) {
    // search by utxo
    return config
        .getPersistHandler()
        .getUtxoConfig(utxo.tx_hash, utxo.tx_output_n); // null if not found
  }

  private WhirlpoolUtxoConfig getUtxoConfigOrNull(String txid) {
    // search by txid
    return config.getPersistHandler().getUtxoConfig(txid); // null if not found
  }

  public WhirlpoolUtxoConfig computeUtxoConfig(
      UnspentOutput utxo, WhirlpoolAccount whirlpoolAccount) {
    // search by utxo
    WhirlpoolUtxoConfig utxoConfig = getUtxoConfigOrNull(utxo);
    if (utxoConfig != null) {
      return utxoConfig;
    }

    // default value
    int mixsDone = 0;
    if (WhirlpoolAccount.POSTMIX.equals(whirlpoolAccount)) {
      // POSTMIX was already mixed once (at least)
      mixsDone++;
    }
    utxoConfig = new WhirlpoolUtxoConfig(mixsDone);
    addUtxoConfig(utxoConfig, utxo.tx_hash, utxo.tx_output_n);

    if (log.isDebugEnabled()) {
      log.debug(
          "New default UtxoConfig for utxo: " + whirlpoolAccount + "/" + utxo + " = " + utxoConfig);
    }
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
      if (!isFirstFetch) {
        if (log.isDebugEnabled()) {
          log.debug(
              firstFetchInfo
                  + "New utxo detected: "
                  + whirlpoolUtxo
                  + " ; (existing utxoConfig) "
                  + utxoConfig);
        }
      } else {
        if (log.isTraceEnabled()) {
          log.trace(
              firstFetchInfo
                  + "New utxo detected: "
                  + whirlpoolUtxo
                  + " ; (existing utxoConfig) "
                  + utxoConfig);
        }
      }
    } else {
      // find by tx hash (new PREMIX from TX0)
      WhirlpoolUtxoConfig utxoConfigByHash = getUtxoConfigOrNull(utxo.tx_hash);
      if (utxoConfigByHash != null) {
        addUtxoConfig(utxoConfigByHash.copy(), utxo.tx_hash, utxo.tx_output_n);
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
        if (pool != null && !isPoolApplicable(pool, whirlpoolUtxo, null)) {
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
  }

  private void autoAssignPool(WhirlpoolUtxo whirlpoolUtxo) throws Exception {
    Collection<Pool> eligiblePools = null;

    // find eligible pools for tx0
    if (WhirlpoolAccount.DEPOSIT.equals(whirlpoolUtxo.getAccount())) {
      Tx0ParamSimple tx0ParamSimple = getTx0ParamSimple(Tx0FeeTarget.MIN);
      eligiblePools = findPoolsForTx0(tx0ParamSimple, whirlpoolUtxo.getUtxo().value, 1, false);
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

  protected void onUtxoRemoved(WhirlpoolUtxo whirlpoolUtxo) {
    mixOrchestrator.onUtxoRemoved(whirlpoolUtxo);
  }

  protected void onUtxoUpdated(WhirlpoolUtxo whirlpoolUtxo, UnspentOutput oldUtxo) {
    int oldConfirmations = oldUtxo.confirmations;
    int freshConfirmations = whirlpoolUtxo.getUtxo().confirmations;

    if (oldConfirmations == 0 && freshConfirmations > 0) {
      if (log.isDebugEnabled()) {
        log.debug("New utxo updated: " + whirlpoolUtxo + " ; " + whirlpoolUtxo.getUtxoConfig());
      }
    }

    // notify autoTx0Orchestrator on TX0_MIN_CONFIRMATIONS
    if (autoTx0Orchestrator.isPresent()
        && wasConfirmed(config.getTx0MinConfirmations(), oldConfirmations, freshConfirmations)) {
      autoTx0Orchestrator.get().onUtxoConfirmed(whirlpoolUtxo);
    }

    // notify mixOrchestrator
    mixOrchestrator.onUtxoUpdated(whirlpoolUtxo);
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

  public WhirlpoolWalletConfig getConfig() {
    return config;
  }

  protected WhirlpoolDataService getDataService() {
    return dataService;
  }
}
