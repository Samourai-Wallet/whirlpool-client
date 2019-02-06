package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.api.client.beans.UnspentOutputPreferredAmountMinComparator;
import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.orchestrator.WalletOrchestrator;
import com.samourai.whirlpool.client.wallet.pushTx.PushTxService;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java8.util.function.Predicate;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
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
  private WhirlpoolClient whirlpoolClient;

  private IIndexHandler feeIndexHandler;
  private Bip84ApiWallet depositWallet;
  private Bip84ApiWallet premixWallet;
  private Bip84ApiWallet postmixWallet;

  // TODO cache expiry
  private Pools pools;
  private Map<String, WhirlpoolUtxo> utxosDeposit;
  private Map<String, WhirlpoolUtxo> utxosPremix;
  private Map<String, WhirlpoolUtxo> utxosPostmix;

  private WalletOrchestrator walletOrchestrator;

  protected WhirlpoolWallet(WhirlpoolWallet whirlpoolWallet, int maxMixClients) {
    this(
        whirlpoolWallet.params,
        whirlpoolWallet.samouraiApi,
        whirlpoolWallet.pushTxService,
        whirlpoolWallet.tx0Service,
        whirlpoolWallet.whirlpoolClient,
        whirlpoolWallet.feeIndexHandler,
        whirlpoolWallet.depositWallet,
        whirlpoolWallet.premixWallet,
        whirlpoolWallet.postmixWallet,
        maxMixClients);
  }

  public WhirlpoolWallet(
      NetworkParameters params,
      SamouraiApi samouraiApi,
      PushTxService pushTxService,
      Tx0Service tx0Service,
      WhirlpoolClient whirlpoolClient,
      IIndexHandler feeIndexHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet,
      int maxMixClients) {
    this.params = params;
    this.samouraiApi = samouraiApi;
    this.pushTxService = pushTxService;
    this.tx0Service = tx0Service;
    this.whirlpoolClient = whirlpoolClient;

    this.feeIndexHandler = feeIndexHandler;
    this.depositWallet = depositWallet;
    this.premixWallet = premixWallet;
    this.postmixWallet = postmixWallet;
    this.walletOrchestrator = new WalletOrchestrator(this, maxMixClients);
  }

  public void clearCache() {
    this.utxosDeposit = null;
    this.utxosPremix = null;
    this.utxosPostmix = null;
  }

  private void fetchUtxosDeposit() throws Exception {
    utxosDeposit = fetchUtxos(depositWallet, WhirlpoolUtxoStatus.DEPOSIT_READY);
  }

  private void fetchUtxosPremix() throws Exception {
    utxosPremix = fetchUtxos(premixWallet, WhirlpoolUtxoStatus.PREMIX_READY);
  }

  private void fetchUtxosPostmix() throws Exception {
    utxosPostmix = fetchUtxos(postmixWallet, WhirlpoolUtxoStatus.POSTMIX_READY);
  }

  private void fetchPools() throws Exception {
    pools = whirlpoolClient.fetchPools();
  }

  private Map<String, WhirlpoolUtxo> fetchUtxos(
      Bip84ApiWallet wallet, WhirlpoolUtxoStatus utxoStatus) throws Exception {
    List<UnspentOutput> fetchedUtxos = wallet.fetchUtxos();
    if (log.isDebugEnabled()) {
      log.debug(
          "Fetching utxos from account #"
              + wallet.getAccountIndex()
              + "... "
              + fetchedUtxos.size()
              + " utxos found");
      ClientUtils.logUtxos(fetchedUtxos);
    }
    Map<String, WhirlpoolUtxo> mapUtxos = new HashMap<String, WhirlpoolUtxo>();
    for (UnspentOutput utxo : fetchedUtxos) {
      mapUtxos.put(utxo.toKey(), new WhirlpoolUtxo(utxo, utxoStatus));
    }
    return mapUtxos;
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
    UnspentOutput utxoSpendFrom = whirlpoolUtxoSpendFrom.getUtxo();

    // check balance min
    final long spendFromBalanceMin = tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, 1);
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

    // refresh utxos
    samouraiApi.refreshUtxos();
    fetchUtxosDeposit();
    fetchUtxosPremix();
    return tx0;
  }

  public void start() throws Exception {
    this.walletOrchestrator.start();
  }

  public void stop() {
    this.walletOrchestrator.stop();
  }

  public void addToMix(WhirlpoolUtxo whirlpoolUtxo) {
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

  protected Pools getPools() throws Exception {
    if (pools == null) {
      fetchPools();
    }
    return pools;
  }

  protected Bip84ApiWallet getDepositWallet() {
    return depositWallet;
  }

  protected Bip84ApiWallet getPremixWallet() {
    return premixWallet;
  }

  protected Bip84ApiWallet getPostmixWallet() {
    return postmixWallet;
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
    if (clearCache || utxosDeposit == null) {
      fetchUtxosDeposit();
    }
    return utxosDeposit.values();
  }

  public Collection<WhirlpoolUtxo> getUtxosPremix(boolean clearCache) throws Exception {
    if (clearCache || utxosPremix == null) {
      fetchUtxosPremix();
    }
    return utxosPremix.values();
  }

  public Collection<WhirlpoolUtxo> getUtxosPostmix(boolean clearCache) throws Exception {
    if (clearCache || utxosPostmix == null) {
      fetchUtxosPostmix();
    }
    return utxosPostmix.values();
  }

  public boolean isStarted() {
    return walletOrchestrator.isStarted();
  }
}
