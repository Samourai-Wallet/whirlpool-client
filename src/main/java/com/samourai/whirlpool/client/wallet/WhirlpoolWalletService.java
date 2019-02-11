package com.samourai.whirlpool.client.wallet;

import com.samourai.api.client.SamouraiApi;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.tx0.Tx0Service;
import com.samourai.whirlpool.client.wallet.pushTx.PushTxService;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolWalletService {
  private final Logger log = LoggerFactory.getLogger(WhirlpoolWalletService.class);

  private NetworkParameters params;
  private SamouraiApi samouraiApi;
  private PushTxService pushTxService;
  private Tx0Service tx0Service;
  private Bech32UtilGeneric bech32Util;
  private WhirlpoolClient whirlpoolClient;
  private WhirlpoolClientConfig whirlpoolClientConfig;
  private int maxClients;
  private int clientDelay;
  private int autoTx0Delay;
  private int autoMixDelay;

  // private WhirlpoolManager whirlpoolManager;

  public WhirlpoolWalletService(
      NetworkParameters params,
      SamouraiApi samouraiApi,
      PushTxService pushTxService,
      Tx0Service tx0Service,
      Bech32UtilGeneric bech32Util,
      WhirlpoolClient whirlpoolClient,
      WhirlpoolClientConfig whirlpoolClientConfig,
      int maxClients,
      int clientDelay,
      int autoTx0Delay,
      int autoMixDelay) {
    this.params = params;
    this.samouraiApi = samouraiApi;
    this.pushTxService = pushTxService;
    this.tx0Service = tx0Service;
    this.bech32Util = bech32Util;
    this.whirlpoolClient = whirlpoolClient;
    this.whirlpoolClientConfig = whirlpoolClientConfig;
    this.maxClients = maxClients;
    this.clientDelay = clientDelay;
    this.autoTx0Delay = autoTx0Delay;
    this.autoMixDelay = autoMixDelay;
  }

  public WhirlpoolWallet openWallet(
      IIndexHandler feeIndexHandler,
      Bip84ApiWallet depositWallet,
      Bip84ApiWallet premixWallet,
      Bip84ApiWallet postmixWallet) {
    return new WhirlpoolWallet(
        params,
        samouraiApi,
        pushTxService,
        tx0Service,
        bech32Util,
        whirlpoolClient,
        whirlpoolClientConfig,
        maxClients,
        clientDelay,
        autoTx0Delay,
        autoMixDelay,
        feeIndexHandler,
        depositWallet,
        premixWallet,
        postmixWallet);
  }
}
