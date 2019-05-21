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
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
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

  public WhirlpoolWalletService() {
    this(null);
  }

  public WhirlpoolWalletService(WhirlpoolClient whirlpoolClient) {
    // set user-agent
    ClientUtils.setupEnv();
  }

  public Pools listPools(WhirlpoolWalletConfig config) throws Exception {
    WhirlpoolClient whirlpoolClient = WhirlpoolClientImpl.newClient(config);
    return whirlpoolClient.fetchPools();
  }

  public WhirlpoolWallet openWallet(WhirlpoolWalletConfig config, HD_Wallet bip84w)
      throws Exception {
    SamouraiApi samouraiApi = config.getSamouraiApi();

    WhirlpoolWalletPersistHandler walletPersistHandler = config.getPersistHandler();

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
    return openWallet(config, depositWallet, premixWallet, postmixWallet);
  }

  public WhirlpoolWallet openWallet(
      WhirlpoolWalletConfig config,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {

    // debug whirlpoolWalletConfig
    if (log.isDebugEnabled()) {
      log.debug("openWallet with whirlpoolWalletConfig:");
      for (Map.Entry<String, String> entry : config.getConfigInfo().entrySet()) {
        log.debug("[whirlpoolWalletConfig/" + entry.getKey() + "] " + entry.getValue());
      }
    }

    Tx0Service tx0Service = new Tx0Service(config.getNetworkParameters());
    Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
    WhirlpoolClient whirlpoolClient = WhirlpoolClientImpl.newClient(config);

    WhirlpoolWallet whirlpoolWallet =
        new WhirlpoolWallet(
            config,
            tx0Service,
            bech32Util,
            whirlpoolClient,
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
}
