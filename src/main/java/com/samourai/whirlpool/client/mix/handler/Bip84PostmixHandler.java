package com.samourai.whirlpool.client.mix.handler;

import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bip84PostmixHandler implements IPostmixHandler {
  private static final Logger log = LoggerFactory.getLogger(Bip84PostmixHandler.class);
  private Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  private Bip84Wallet postmixWallet;
  private HD_Address receiveAddress;
  private Integer receiveAddressIndex;

  public Bip84PostmixHandler(Bip84Wallet postmixWallet) {
    this.postmixWallet = postmixWallet;
    this.receiveAddress = null;
    this.receiveAddressIndex = null;
  }

  @Override
  public synchronized String computeReceiveAddress(NetworkParameters params) throws Exception {
    // use "unconfirmed" index to avoid huge index gaps on multiple mix failures
    this.receiveAddressIndex = postmixWallet.getIndexHandler().getAndIncrementUnconfirmed();
    this.receiveAddress =
        postmixWallet.getAddressAt(Bip84Wallet.CHAIN_RECEIVE, this.receiveAddressIndex);

    String bech32Address = bech32Util.toBech32(receiveAddress, params);
    if (log.isDebugEnabled()) {
      log.debug(
          "receiveAddress=" + bech32Address + ", path=" + receiveAddress.toJSON().get("path"));
    }
    return bech32Address;
  }

  @Override
  public void confirmReceiveAddress() {
    postmixWallet.getIndexHandler().confirmUnconfirmed(receiveAddressIndex);
  }

  @Override
  public void cancelReceiveAddress() {
    if (receiveAddressIndex != null) {
      postmixWallet.getIndexHandler().cancelUnconfirmed(receiveAddressIndex);
    }
  }
}
