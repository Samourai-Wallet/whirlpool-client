package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletAccount;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWalletService {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWalletService.class);

  private static final String INDEX_DEPOSIT = "deposit";
  private static final String INDEX_DEPOSIT_CHANGE = "deposit_change";
  private static final String INDEX_PREMIX = "premix";
  private static final String INDEX_PREMIX_CHANGE = "premix_change";
  private static final String INDEX_POSTMIX = "postmix";
  private static final String INDEX_POSTMIX_CHANGE = "postmix_change";

  private WhirlpoolWalletConfig config;

  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  public WhirlpoolWalletService(WhirlpoolWalletConfig whirlpoolWalletConfig) {
    this(whirlpoolWalletConfig, null);

    ClientUtils.setupEnv();
  }

  public WhirlpoolWalletService(WhirlpoolWalletConfig config, WhirlpoolClient whirlpoolClient) {
    this.config = config;

    this.tx0Service = new Tx0Service(config.getNetworkParameters(), config.getFeeXpub());
    this.bech32Util = Bech32UtilGeneric.getInstance();

    if (whirlpoolClient == null) {
      whirlpoolClient = WhirlpoolClientImpl.newClient(config);
    }
    this.whirlpoolClient = whirlpoolClient;
  }

  public boolean testConnectivity() {
    try {
      this.whirlpoolClient.fetchPools();
      return true;
    } catch (Exception e) {
      log.error("", e);
      return false;
    }
  }

  public WhirlpoolWallet openWallet(
      HD_Wallet bip84w, WhirlpoolWalletPersistHandler walletPersistHandler) throws Exception {
    SamouraiApi samouraiApi = config.getSamouraiApi();

    IIndexHandler depositIndexHandler = walletPersistHandler.getIndexHandler(INDEX_DEPOSIT);
    IIndexHandler depositChangeIndexHandler =
        walletPersistHandler.getIndexHandler(INDEX_DEPOSIT_CHANGE);
    IIndexHandler premixIndexHandler = walletPersistHandler.getIndexHandler(INDEX_PREMIX);
    IIndexHandler premixChangeIndexHandler =
        walletPersistHandler.getIndexHandler(INDEX_PREMIX_CHANGE);
    IIndexHandler postmixIndexHandler = walletPersistHandler.getIndexHandler(INDEX_POSTMIX);
    IIndexHandler postmixChangeIndexHandler =
        walletPersistHandler.getIndexHandler(INDEX_POSTMIX_CHANGE);
    boolean init = !walletPersistHandler.isInitialized();

    // deposit, premix & postmix wallets
    Bip84ApiWallet depositWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.DEPOSIT.getAccountIndex(),
            depositIndexHandler,
            depositChangeIndexHandler,
            samouraiApi,
            init);
    Bip84ApiWallet premixWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.PREMIX.getAccountIndex(),
            premixIndexHandler,
            premixChangeIndexHandler,
            samouraiApi,
            init);
    Bip84ApiWallet postmixWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.POSTMIX.getAccountIndex(),
            postmixIndexHandler,
            postmixChangeIndexHandler,
            samouraiApi,
            init);

    if (init) {
      walletPersistHandler.setInitialized(true);
    }
    return openWallet(walletPersistHandler, depositWallet, premixWallet, postmixWallet);
  }

  public WhirlpoolWallet openWallet(
      WhirlpoolWalletPersistHandler walletPersistHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    WhirlpoolWallet whirlpoolWallet =
        new WhirlpoolWallet(
            config,
            tx0Service,
            bech32Util,
            whirlpoolClient,
            walletPersistHandler,
            depositWallet,
            premixWallet,
            postmixWallet);

    // log zpubs
    if (log.isDebugEnabled()) {
      String depositZpub = depositWallet.getZpub();
      String premixZpub = premixWallet.getZpub();
      String postmixZpub = postmixWallet.getZpub();
      log.debug(
          "Deposit wallet: accountIndex="
              + depositWallet.getAccountIndex()
              + ", zpub="
              + depositZpub
              + ", receiveIndex="
              + depositWallet.getIndexHandler().get()
              + ", changeIndex="
              + depositWallet.getIndexChangeHandler().get());
      log.debug(
          "Premix wallet: accountIndex="
              + premixWallet.getAccountIndex()
              + ", zpub="
              + premixZpub
              + ", receiveIndex="
              + premixWallet.getIndexHandler().get()
              + ", changeIndex="
              + premixWallet.getIndexChangeHandler().get());
      log.debug(
          "Postmix wallet: accountIndex="
              + postmixWallet.getAccountIndex()
              + ", zpub="
              + postmixZpub
              + ", receiveIndex="
              + postmixWallet.getIndexHandler().get()
              + ", changeIndex="
              + postmixWallet.getIndexChangeHandler().get());
    }

    // log indexs
    log.info(
        "Deposit wallet: receiveIndex="
            + depositWallet.getIndexHandler().get()
            + ", changeIndex="
            + depositWallet.getIndexChangeHandler().get());
    log.info(
        "Premix wallet: receiveIndex="
            + premixWallet.getIndexHandler().get()
            + ", changeIndex="
            + premixWallet.getIndexChangeHandler().get());
    log.info(
        "Postmix wallet: receiveIndex="
            + postmixWallet.getIndexHandler().get()
            + ", changeIndex="
            + postmixWallet.getIndexChangeHandler().get());

    return whirlpoolWallet;
  }

  public Map<String, String> getConfigInfo() {
    Map<String, String> configInfo = new LinkedHashMap<String, String>();
    String feeX = config.getFeeXpub();
    configInfo.put(
        "server",
        "url="
            + config.getServer()
            + ", network="
            + config.getNetworkParameters().getPaymentProtocolId()
            + ", ssl="
            + Boolean.toString(config.isSsl())
            + ", feeX="
            + ClientUtils.maskString(feeX, 6, 4));
    configInfo.put("pushtx", config.getPushTxService().getClass().getName());
    configInfo.put(
        "persist",
        "persistDelay="
            + Integer.toString(config.getPersistDelay())
            + ", persistCleanDelay="
            + Integer.toString(config.getPersistCleanDelay()));
    configInfo.put(
        "mix",
        "maxClients="
            + config.getMaxClients()
            + ", clientDelay="
            + config.getClientDelay()
            + ", tx0Delay="
            + config.getTx0Delay()
            + ", tx0MaxOutputs="
            + config.getTx0MaxOutputs()
            + ", autoTx0="
            + (config.isAutoTx0() ? config.getAutoTx0PoolId() : "false")
            + ", autoTx0FeeTarget="
            + config.getAutoTx0FeeTarget().name()
            + ", autoMix="
            + config.isAutoMix()
            + ", mixsTarget="
            + config.getMixsTarget());
    configInfo.put(
        "fee",
        "fallback="
            + config.getFeeFallback()
            + ", min="
            + config.getFeeMin()
            + ", max="
            + config.getFeeMax()
            + ", targetPremix="
            + config.getFeeTargetPremix());
    return configInfo;
  }
}
