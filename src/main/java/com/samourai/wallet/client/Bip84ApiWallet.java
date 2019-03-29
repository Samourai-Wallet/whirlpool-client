package com.samourai.wallet.client;

import com.samourai.api.client.SamouraiApi;
import com.samourai.api.client.beans.MultiAddrResponse;
import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
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
  private SamouraiApi samouraiApi;

  public Bip84ApiWallet(
      HD_Wallet bip84w,
      int accountIndex,
      IIndexHandler indexHandler,
      IIndexHandler indexChangeHandler,
      SamouraiApi samouraiApi,
      boolean init)
      throws Exception {
    super(bip84w, accountIndex, indexHandler, indexChangeHandler);
    this.samouraiApi = samouraiApi;

    if (init) {
      initBip84();
    }

    // fetch index from API
    MultiAddrResponse.Address address = fetchAddress();

    // account_index
    if (indexHandler.get() < address.account_index) {
      if (log.isDebugEnabled()) {
        log.debug(
            "wallet #"
                + accountIndex
                + "/account_index: apiIndex="
                + address.account_index
                + ", localIndex="
                + indexHandler.get()
                + " => using apiIndex");
      }
      indexHandler.set(address.account_index);
    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "wallet #"
                + accountIndex
                + ": apiIndex="
                + address.account_index
                + ", localIndex="
                + indexHandler.get()
                + " => using localIndex");
      }
    }

    // change_index
    if (indexChangeHandler.get() < address.change_index) {
      if (log.isDebugEnabled()) {
        log.debug(
            "wallet #"
                + accountIndex
                + "/change_index: apiIndex="
                + address.change_index
                + ", localIndex="
                + indexChangeHandler.get()
                + " => using apiIndex");
      }
      indexChangeHandler.set(address.change_index);
    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "wallet #"
                + accountIndex
                + "/change_index: apiIndex="
                + address.change_index
                + ", localIndex="
                + indexChangeHandler.get()
                + " => using localIndex");
      }
    }
  }

  public List<UnspentOutput> fetchUtxos() throws Exception {
    String zpub = getZpub();
    return samouraiApi.fetchUtxos(zpub);
  }

  private MultiAddrResponse.Address fetchAddress() throws Exception {
    String zpub = getZpub();
    MultiAddrResponse.Address address = samouraiApi.fetchAddress(zpub);
    if (address == null) {
      throw new Exception("Address not found");
    }
    return address;
  }

  public void initBip84() throws Exception {
    for (int i = 0; i < INIT_BIP84_RETRY; i++) {
      log.info(" • Initializing bip84 wallet: " + accountIndex);
      try {
        samouraiApi.initBip84(getZpub());
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
