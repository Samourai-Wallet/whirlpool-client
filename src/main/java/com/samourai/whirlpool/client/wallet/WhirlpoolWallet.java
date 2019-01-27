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
  private Map<String, UnspentOutput> utxosDeposit;
  private Map<String, UnspentOutput> utxosPremix;
  private Map<String, UnspentOutput> utxosPostmix;

  protected WhirlpoolWallet(WhirlpoolWallet whirlpoolWallet) {
    this(
        whirlpoolWallet.params,
        whirlpoolWallet.samouraiApi,
        whirlpoolWallet.pushTxService,
        whirlpoolWallet.tx0Service,
        whirlpoolWallet.whirlpoolClient,
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
      WhirlpoolClient whirlpoolClient,
      IIndexHandler feeIndexHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    this.params = params;
    this.samouraiApi = samouraiApi;
    this.pushTxService = pushTxService;
    this.tx0Service = tx0Service;
    this.whirlpoolClient = whirlpoolClient;

    this.feeIndexHandler = feeIndexHandler;
    this.depositWallet = depositWallet;
    this.premixWallet = premixWallet;
    this.postmixWallet = postmixWallet;
  }

  public void clearCache() {
    this.utxosDeposit = null;
    this.utxosPremix = null;
    this.utxosPostmix = null;
  }

  private void fetchUtxosDeposit() throws Exception {
    utxosDeposit = fetchUtxos(depositWallet);
  }

  private void fetchUtxosPremix() throws Exception {
    utxosPremix = fetchUtxos(premixWallet);
  }

  private void fetchUtxosPostmix() throws Exception {
    utxosPostmix = fetchUtxos(postmixWallet);
  }

  private void fetchPools() throws Exception {
    pools = whirlpoolClient.fetchPools();
  }

  private Map<String, UnspentOutput> fetchUtxos(Bip84ApiWallet wallet) throws Exception {
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
    Map<String, UnspentOutput> mapUtxos = new HashMap<String, UnspentOutput>();
    for (UnspentOutput utxo : fetchedUtxos) {
      mapUtxos.put(utxoIndice(utxo), utxo);
    }
    return mapUtxos;
  }

  public Tx0 tx0(Pool pool) throws Exception {
    return tx0(pool, Tx0Service.NB_PREMIX_MAX, 1);
  }

  public Tx0 tx0(Pool pool, int nbOutputsPreferred, int nbOutputsMin) throws Exception {
    int feeSatPerByte = samouraiApi.fetchFees();

    // find utxo to spend Tx0 from
    final long spendFromBalanceMin =
        tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsMin);
    final long spendFromBalancePreferred =
        tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsPreferred);

    List<UnspentOutput> depositSpendFroms =
        filterUtxos(spendFromBalanceMin, spendFromBalancePreferred, getUtxosDeposit(true));
    if (depositSpendFroms.isEmpty()) {
      long balanceRequired =
          tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsMin);
      throw new EmptyWalletException("Insufficient balance for Tx0", balanceRequired);
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
      ClientUtils.logUtxos(depositSpendFroms);
    }
    UnspentOutput depositSpendFrom = depositSpendFroms.get(0);

    // spend whole utxo
    Tx0 tx0 = tx0(pool, depositSpendFrom, feeSatPerByte, nbOutputsPreferred);
    return tx0;
  }

  public synchronized Tx0 tx0(
      Pool pool, UnspentOutput depositSpendFrom, int feeSatPerByte, int nbOutputsPreferred)
      throws Exception {
    log.info(
        " • Tx0: poolId="
            + pool.getPoolId()
            + ", depositSpendFrom="
            + depositSpendFrom
            + ", nbOutputsPreferred="
            + nbOutputsPreferred);

    // spend from
    TransactionOutPoint spendFromOutpoint = depositSpendFrom.computeOutpoint(params);
    byte[] spendFromPrivKey =
        depositWallet.getAddressAt(depositSpendFrom).getECKey().getPrivKeyBytes();

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

    // fetch utxos from premix
    samouraiApi.refreshUtxos();
    fetchUtxosPremix();
    return tx0;
  }

  private List<UnspentOutput> filterUtxos(
      final long balanceMin, final long balancePreferred, Collection<UnspentOutput> utxos) {
    if (utxos.isEmpty()) {
      return new ArrayList<UnspentOutput>();
    }
    return StreamSupport.stream(utxosDeposit.values())
        .filter(
            new Predicate<UnspentOutput>() {
              @Override
              public boolean test(UnspentOutput utxo) {
                return utxo.value >= balanceMin;
              }
            })

        // take UTXO closest to balancePreferred (and higher when possible)
        .sorted(new UnspentOutputPreferredAmountMinComparator(balancePreferred))
        .collect(Collectors.<UnspentOutput>toList());
  }

  private String utxoIndice(UnspentOutput utxo) {
    return utxo.tx_hash + ":" + utxo.tx_output_n;
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

  public Collection<UnspentOutput> getUtxosDeposit() throws Exception {
    return getUtxosDeposit(false);
  }

  public Collection<UnspentOutput> getUtxosPremix() throws Exception {
    return getUtxosPremix(false);
  }

  public Collection<UnspentOutput> getUtxosPostmix() throws Exception {
    return getUtxosPostmix(false);
  }

  public Collection<UnspentOutput> getUtxosDeposit(boolean clearCache) throws Exception {
    if (clearCache || utxosDeposit == null) {
      fetchUtxosDeposit();
    }
    return utxosDeposit.values();
  }

  public Collection<UnspentOutput> getUtxosPremix(boolean clearCache) throws Exception {
    if (clearCache || utxosPremix == null) {
      fetchUtxosPremix();
    }
    return utxosPremix.values();
  }

  public Collection<UnspentOutput> getUtxosPostmix(boolean clearCache) throws Exception {
    if (clearCache || utxosPostmix == null) {
      fetchUtxosPostmix();
    }
    return utxosPostmix.values();
  }
}
