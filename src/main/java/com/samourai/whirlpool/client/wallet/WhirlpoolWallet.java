package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.api.client.beans.UnspentOutputPreferredAmountMinComparator;
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
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.wallet.orchestrator.WalletOrchestrator;
import com.samourai.whirlpool.client.wallet.pushTx.PushTxService;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  // TODO cache expiry
  private Pools pools;
  private Map<String, WhirlpoolUtxo> utxos;

  private WalletOrchestrator walletOrchestrator;

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

    this.walletOrchestrator = new WalletOrchestrator(this, maxClients, clientDelay);
  }

  private void fetchPools() throws Exception {
    pools = whirlpoolClient.fetchPools();
  }

  public void clearCache() {
    this.utxos = null;
  }

  private synchronized void fetchUtxos(WhirlpoolAccount account) throws Exception {
    // fetch new utxos
    Bip84ApiWallet wallet = getWallet(account);
    List<UnspentOutput> fetchedUtxos = wallet.fetchUtxos();
    if (log.isDebugEnabled()) {
      log.debug("Fetching utxos from " + account + "... " + fetchedUtxos.size() + " utxos found");
      ClientUtils.logUtxos(fetchedUtxos);
    }
    final Map<String, WhirlpoolUtxo> freshUtxos = new HashMap<String, WhirlpoolUtxo>();
    for (UnspentOutput utxo : fetchedUtxos) {
      freshUtxos.put(utxo.toKey(), new WhirlpoolUtxo(utxo, account, WhirlpoolUtxoStatus.READY));
    }

    // replace utxos
    replaceUtxos(account, freshUtxos);
  }

  private void replaceUtxos(WhirlpoolAccount account, final Map<String, WhirlpoolUtxo> freshUtxos)
      throws Exception {
    Collection<WhirlpoolUtxo> currentUtxos;

    if (utxos != null) {
      currentUtxos = getUtxos(account, false);
    } else {
      currentUtxos = new ArrayList<WhirlpoolUtxo>();
      utxos = new HashMap<String, WhirlpoolUtxo>();
    }

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
            new Consumer<WhirlpoolUtxo>() {
              @Override
              public void accept(WhirlpoolUtxo whirlpoolUtxo) {
                String key = whirlpoolUtxo.getUtxo().toKey();
                if (!utxos.containsKey(key)) {
                  // add missing
                  utxos.put(key, whirlpoolUtxo);
                  onUtxoAdded(whirlpoolUtxo);
                }
              }
            });
  }

  public WhirlpoolUtxo findUtxoDepositForTx0(Pool pool) throws Exception {
    return findUtxoDepositForTx0(pool, Tx0Service.NB_PREMIX_MAX, 1);
  }

  public WhirlpoolUtxo findUtxoDepositForTx0(Pool pool, int nbOutputsPreferred, int nbOutputsMin)
      throws Exception {
    int feeSatPerByte = samouraiApi.fetchFees();

    // find utxo to spend Tx0 from
    final long spendFromBalanceMin =
        tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsMin);
    final long spendFromBalancePreferred =
        tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsPreferred);

    List<WhirlpoolUtxo> depositSpendFroms =
        filterUtxosByBalancePreferred(
            spendFromBalanceMin, spendFromBalancePreferred, getUtxosDeposit(true));
    if (depositSpendFroms.isEmpty()) {
      throw new EmptyWalletException("Insufficient balance for Tx0", spendFromBalanceMin);
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Found "
              + depositSpendFroms.size()
              + " utxos to use as Tx0 input for spendFromBalanceMin="
              + spendFromBalanceMin
              + ", spendFromBalancePreferred="
              + spendFromBalancePreferred
              + ", nbOutputsMin="
              + nbOutputsMin
              + ", nbOutputsPreferred="
              + nbOutputsPreferred);
      ClientUtils.logWhirlpoolUtxos(depositSpendFroms);
    }
    WhirlpoolUtxo whirlpoolUtxoSpendFrom = depositSpendFroms.get(0);
    return whirlpoolUtxoSpendFrom;
  }

  public synchronized Tx0 tx0(
      Pool pool, int nbOutputsPreferred, WhirlpoolUtxo whirlpoolUtxoSpendFrom) throws Exception {
    int feeSatPerByte = samouraiApi.fetchFees();
    return tx0(pool, nbOutputsPreferred, whirlpoolUtxoSpendFrom, feeSatPerByte);
  }

  public synchronized Tx0 tx0(
      Pool pool, int nbOutputsPreferred, WhirlpoolUtxo whirlpoolUtxoSpendFrom, int feeSatPerByte)
      throws Exception {
    whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO);
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
      whirlpoolUtxoSpendFrom.setStatus(WhirlpoolUtxoStatus.TXO_SUCCESS);
      whirlpoolUtxoSpendFrom.setMessage("TX0 txid: " + tx0.getTx().getHashAsString());

      // refresh utxos
      samouraiApi.refreshUtxos();
      fetchUtxos(WhirlpoolAccount.DEPOSIT);
      fetchUtxos(WhirlpoolAccount.PREMIX);
      return tx0;
    } catch (Exception e) {
      // error
      whirlpoolUtxoSpendFrom.setError(e);
      throw e;
    }
  }

  public void start() {
    log.info(" • Starting WhirlpoolWallet");
    this.walletOrchestrator.start();
  }

  public void stop() {
    if (isStarted()) {
      log.info(" • Stopping WhirlpoolWallet");
      this.walletOrchestrator.stop();
    }
  }

  public void addToMix(WhirlpoolUtxo whirlpoolUtxo, Pool pool) {
    whirlpoolUtxo.setPool(pool);
    this.walletOrchestrator.addToMix(whirlpoolUtxo);
  }

  private List<WhirlpoolUtxo> filterUtxosByBalancePreferred(
      final long balanceMin, final long balancePreferred, Collection<WhirlpoolUtxo> utxos) {
    if (utxos.isEmpty()) {
      return new ArrayList<WhirlpoolUtxo>();
    }
    return StreamSupport.stream(utxos)
        .filter(
            new Predicate<WhirlpoolUtxo>() {
              @Override
              public boolean test(WhirlpoolUtxo utxo) {
                return utxo.getUtxo().value >= balanceMin;
              }
            })

        // take UTXO closest to balancePreferred (and higher when possible)
        .sorted(new UnspentOutputPreferredAmountMinComparator(balancePreferred))
        .collect(Collectors.<WhirlpoolUtxo>toList());
  }

  public Pools getPools() throws Exception {
    if (pools == null) {
      fetchPools();
    }
    return pools;
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
    whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_STARTED);
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
          public void fail(int currentMix, int nbMixs) {
            super.fail(currentMix, nbMixs);
            whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_FAILED);
          }

          @Override
          public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
            super.mixSuccess(currentMix, nbMixs, mixSuccess);
            whirlpoolUtxo.setStatus(WhirlpoolUtxoStatus.MIX_SUCCESS);
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
    if (clearCache || utxos == null) {
      fetchUtxos(account);
    }
    return findUtxos(account);
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
    return walletOrchestrator.isStarted();
  }

  public WhirlpoolWalletState getState() {
    return walletOrchestrator.getState();
  }

  public String getDepositAddress(boolean increment) {
    return bech32Util.toBech32(depositWallet.getNextAddress(increment), params);
  }

  protected void onUtxoAdded(WhirlpoolUtxo whirlpoolUtxo) {
    if (log.isDebugEnabled()) {
      log.debug(" o New UTXO detected: " + whirlpoolUtxo);
    }
    walletOrchestrator.onUtxoAdded(whirlpoolUtxo);
  }

  protected void onUtxoRemoved(WhirlpoolUtxo whirlpoolUtxo) {}
}
