package com.samourai.wallet.client;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bip84Wallet {
  private static final Logger log = LoggerFactory.getLogger(Bip84Wallet.class);
  protected static final int CHAIN_RECEIVE = 0;
  protected static final int CHAIN_CHANGE = 1;

  protected HD_Wallet bip84w;
  protected int accountIndex;
  protected IIndexHandler indexHandler;
  protected IIndexHandler indexChangeHandler;

  public Bip84Wallet(
      HD_Wallet bip84w,
      int accountIndex,
      IIndexHandler indexHandler,
      IIndexHandler indexChangeHandler) {
    this.bip84w = bip84w;
    this.accountIndex = accountIndex;
    this.indexHandler = indexHandler;
    this.indexChangeHandler = indexChangeHandler;
  }

  public HD_Address getNextAddress() {
    return getNextAddress(true);
  }

  public HD_Address getNextAddress(boolean increment) {
    int nextAddressIndex = increment ? indexHandler.getAndIncrement() : indexHandler.get();
    return getAddressAt(CHAIN_RECEIVE, nextAddressIndex);
  }

  public HD_Address getNextChangeAddress() {
    return getNextChangeAddress(true);
  }

  public HD_Address getNextChangeAddress(boolean increment) {
    int nextAddressIndex =
        increment ? indexChangeHandler.getAndIncrement() : indexChangeHandler.get();
    return getAddressAt(CHAIN_CHANGE, nextAddressIndex);
  }

  public HD_Address getAddressAt(int chainIndex, int addressIndex) {
    return getAddressBip84(accountIndex, chainIndex, addressIndex);
  }

  public HD_Address getAddressAt(UnspentResponse.UnspentOutput utxo) {
    return getAddressAt(utxo.computePathChainIndex(), utxo.computePathAddressIndex());
  }

  public String getZpub() {
    return bip84w.getAccountAt(accountIndex).zpubstr();
  }

  private HD_Address getAddressBip84(int account, int chain, int index) {
    return bip84w.getAccountAt(account).getChain(chain).getAddressAt(index);
  }

  public IIndexHandler getIndexHandler() {
    return indexHandler;
  }

  public IIndexHandler getIndexChangeHandler() {
    return indexChangeHandler;
  }

  public int getAccountIndex() {
    return accountIndex;
  }
}
