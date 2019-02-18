package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletAccount;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWalletService {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWalletService.class);

  private WhirlpoolWalletConfig config;

  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;

  private WhirlpoolClient whirlpoolClient;

  public WhirlpoolWalletService(WhirlpoolWalletConfig whirlpoolWalletConfig) {
    this(whirlpoolWalletConfig, null);
  }

  public WhirlpoolWalletService(WhirlpoolWalletConfig config, WhirlpoolClient whirlpoolClient) {
    this.config = config;

    this.tx0Service =
        new Tx0Service(config.getNetworkParameters(), config.getFeeXpub(), config.getFeeValue());
    this.bech32Util = Bech32UtilGeneric.getInstance();

    if (whirlpoolClient == null) {
      whirlpoolClient = WhirlpoolClientImpl.newClient(config);
    }
    this.whirlpoolClient = whirlpoolClient;
  }

  public WhirlpoolWallet openWallet(
      HD_Wallet bip84w,
      IIndexHandler depositIndexHandler,
      IIndexHandler depositChangeIndexHandler,
      IIndexHandler premixIndexHandler,
      IIndexHandler premixChangeIndexHandler,
      IIndexHandler postmixIndexHandler,
      IIndexHandler postmixChangeIndexHandler,
      IIndexHandler feeIndexHandler)
      throws Exception {
    return openWallet(
        bip84w,
        depositIndexHandler,
        depositChangeIndexHandler,
        premixIndexHandler,
        premixChangeIndexHandler,
        postmixIndexHandler,
        postmixChangeIndexHandler,
        feeIndexHandler,
        false);
  }

  public WhirlpoolWallet openWallet(
      HD_Wallet bip84w,
      IIndexHandler depositIndexHandler,
      IIndexHandler depositChangeIndexHandler,
      IIndexHandler premixIndexHandler,
      IIndexHandler premixChangeIndexHandler,
      IIndexHandler postmixIndexHandler,
      IIndexHandler postmixChangeIndexHandler,
      IIndexHandler feeIndexHandler,
      boolean initBip84)
      throws Exception {

    SamouraiApi samouraiApi = config.getSamouraiApi();

    // deposit, premix & postmix wallets
    Bip84ApiWallet depositWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.DEPOSIT.getAccountIndex(),
            depositIndexHandler,
            depositChangeIndexHandler,
            samouraiApi,
            initBip84);
    Bip84ApiWallet premixWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.PREMIX.getAccountIndex(),
            premixIndexHandler,
            premixChangeIndexHandler,
            samouraiApi,
            initBip84);
    Bip84ApiWallet postmixWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.POSTMIX.getAccountIndex(),
            postmixIndexHandler,
            postmixChangeIndexHandler,
            samouraiApi,
            initBip84);
    return openWallet(feeIndexHandler, depositWallet, premixWallet, postmixWallet);
  }

  public WhirlpoolWallet openWallet(
      IIndexHandler feeIndexHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    WhirlpoolWallet whirlpoolWallet =
        new WhirlpoolWallet(
            config,
            tx0Service,
            bech32Util,
            whirlpoolClient,
            feeIndexHandler,
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
