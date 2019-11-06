package com.samourai.wallet.client;

import com.samourai.api.client.WhirlpoolBackendApi;
import com.samourai.wallet.api.backend.beans.MultiAddrResponse;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.exception.NotifiableException;
import java.util.List;
import java8.util.function.ToLongFunction;
import java8.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bip84ApiWallet extends Bip84Wallet {
  private static final Logger log = LoggerFactory.getLogger(Bip84ApiWallet.class);
  private static final int INIT_BIP84_RETRY = 3;
  private static final int INIT_BIP84_RETRY_TIMEOUT = 5000;
  private WhirlpoolBackendApi backendApi;

  public Bip84ApiWallet(
      HD_Wallet bip84w,
      int accountIndex,
      IIndexHandler indexHandler,
      IIndexHandler indexChangeHandler,
      WhirlpoolBackendApi backendApi,
      boolean init)
      throws Exception {
    this(bip84w, accountIndex, indexHandler, indexChangeHandler, backendApi, init, null, null);
  }

  public Bip84ApiWallet(
      HD_Wallet bip84w,
      int accountIndex,
      IIndexHandler indexHandler,
      IIndexHandler indexChangeHandler,
      WhirlpoolBackendApi backendApi,
      boolean init,
      Integer accountIndexApi,
      Integer changeIndexApi)
      throws Exception {
    super(bip84w, accountIndex, indexHandler, indexChangeHandler);
    this.backendApi = backendApi;

    if (init) {
      initBip84();
    }

    if (accountIndexApi == null || changeIndexApi == null) {
      MultiAddrResponse.Address address = fetchAddress();
      accountIndexApi = address.account_index;
      changeIndexApi = address.change_index;
    }
    setIndexMin(accountIndexApi, indexHandler);
    setIndexMin(changeIndexApi, indexChangeHandler);
  }

  public void refreshIndexs() throws Exception {
    MultiAddrResponse.Address address = fetchAddress();
    setIndexMin(address.account_index, indexHandler);
    setIndexMin(address.change_index, indexChangeHandler);
  }

  private void setIndexMin(int indexMin, IIndexHandler idxHandler) {
    if (idxHandler.get() < indexMin) {
      // update from indexMin
      if (log.isDebugEnabled()) {
        log.debug(
            "wallet #"
                + accountIndex
                + ": apiIndex="
                + indexMin
                + ", localIndex="
                + idxHandler.get()
                + " => updating from apiIndex");
      }
      idxHandler.set(indexMin);
    } else {
      // index unchanged
      if (log.isDebugEnabled()) {
        log.debug(
            "wallet #"
                + accountIndex
                + ": apiIndex="
                + indexMin
                + ", localIndex="
                + idxHandler.get()
                + " => unchanged");
      }
    }
  }

  public List<UnspentOutput> fetchUtxos() throws Exception {
    String zpub = getZpub();
    return backendApi.fetchUtxos(zpub);
  }

  private MultiAddrResponse.Address fetchAddress() throws Exception {
    String zpub = getZpub();
    MultiAddrResponse.Address address = backendApi.fetchAddress(zpub);
    if (address == null) {
      throw new Exception("Address not found");
    }
    return address;
  }

  public void initBip84() throws Exception {
    for (int i = 0; i < INIT_BIP84_RETRY; i++) {
      log.info(" • Initializing bip84 wallet: " + accountIndex);
      try {
        backendApi.initBip84(getZpub());
        return; // success
      } catch (Exception e) {
        if (log.isDebugEnabled()) {
          log.error("", e);
        }
        log.error(
            " x Initializing bip84 wallet failed, retrying... ("
                + (i + 1)
                + "/"
                + INIT_BIP84_RETRY
                + ")");
        Thread.sleep(INIT_BIP84_RETRY_TIMEOUT);
      }
    }
    throw new NotifiableException("Unable to initialize Bip84 wallet");
  }

  public long fetchBalance() throws Exception {
    return StreamSupport.stream(fetchUtxos())
        .mapToLong(
            new ToLongFunction<UnspentOutput>() {
              @Override
              public long applyAsLong(UnspentOutput utxo) {
                return utxo.value;
              }
            })
        .sum();
  }
}
