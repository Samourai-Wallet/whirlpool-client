package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.api.client.beans.UnspentResponse;
import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.utils.PushTxService;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import java.util.List;
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

  private WhirlpoolManager whirlpoolManager;
  private Pools pools;

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
    reset();
  }

  private void reset() {
    this.whirlpoolManager = new WhirlpoolManager();
  }

  public synchronized void start() throws Exception {
    log.info(" • Starting WhirlpoolWallet...");

    reset();
    whirlpoolManager.start();

    // fetch pools
    log.info(" • Fetching pools...");
    pools = whirlpoolClient.fetchPools();

    // add utxos from premix
    addFromPremix();

    whirlpoolManager.start();
  }

  private void addFromPremix() throws Exception {
    List<UnspentOutput> premixUtxos = premixWallet.fetchUtxos();
    log.info(" • Adding utxos from premix: " + premixUtxos.size() + " utxos to add");
    if (log.isDebugEnabled()) {
      ClientUtils.logUtxos(premixUtxos);
    }
    for (UnspentOutput utxo : premixUtxos) {
      whirlpoolManager.add(utxo.tx_hash, utxo.tx_output_n);
    }
  }

  public Tx0 tx0(Pool pool) throws Exception {
    return tx0(pool, 1);
  }

  public Tx0 tx0(Pool pool, int nbOutputsMin) throws Exception {
    List<UnspentResponse.UnspentOutput> utxos = depositWallet.fetchUtxos();
    if (utxos.isEmpty()) {
      throw new EmptyWalletException("No utxo found from deposit.");
    }

    if (log.isDebugEnabled()) {
      log.debug("Found " + utxos.size() + " utxo from deposit:");
      ClientUtils.logUtxos(utxos);
    }

    // fetch spend address info
    int feeSatPerByte = samouraiApi.fetchFees();

    // find utxo to spend Tx0 from
    final long spendFromBalanceMin =
        tx0Service.computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsMin);
    List<UnspentResponse.UnspentOutput> tx0SpendFroms =
        StreamSupport.stream(utxos)
            .filter(
                new Predicate<UnspentOutput>() {
                  @Override
                  public boolean test(UnspentOutput utxo) {
                    return utxo.value >= spendFromBalanceMin;
                  }
                })
            .collect(Collectors.<UnspentResponse.UnspentOutput>toList());

    if (tx0SpendFroms.isEmpty()) {
      throw new EmptyWalletException("ERROR: No utxo available to spend Tx0 from");
    }
    if (log.isDebugEnabled()) {
      log.debug("Found " + tx0SpendFroms.size() + " utxos to use as Tx0 input");
      ClientUtils.logUtxos(tx0SpendFroms);
    }
    UnspentResponse.UnspentOutput depositSpendFrom = tx0SpendFroms.get(0);

    Tx0 tx0 = tx0(pool, depositSpendFrom, feeSatPerByte);
    return tx0;
  }

  public Tx0 tx0(Pool pool, UnspentResponse.UnspentOutput depositSpendFrom, int feeSatPerByte)
      throws Exception {
    log.info(" • Tx0: poolId=" + pool.getPoolId() + ", depositSpendFrom=" + depositSpendFrom);

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
            feeSatPerByte,
            feeIndexHandler,
            pools,
            pool);

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

    // add utxos from premix
    samouraiApi.refreshUtxos();
    addFromPremix();

    return tx0;
  }

  public synchronized void stop() {
    log.info(" • Stopping WhirlpoolWallet...");
    whirlpoolManager.stop();
  }

  public boolean isStarted() {
    return whirlpoolManager.isStarted();
  }

  public WhirlpoolWalletState getState() {
    return null; // TODO
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

  public Tx0Service getTx0Service() {
    return tx0Service;
  }
}
