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

  public Bip84PostmixHandler(Bip84Wallet postmixWallet) {
    this.postmixWallet = postmixWallet;
    this.receiveAddress = null;
  }

  @Override
  public synchronized String computeReceiveAddress(NetworkParameters params) throws Exception {
    this.receiveAddress = postmixWallet.getNextAddress();

    String bech32Address = bech32Util.toBech32(receiveAddress, params);
    if (log.isDebugEnabled()) {
      log.debug(
          "receiveAddress=" + bech32Address + ", path=" + receiveAddress.toJSON().get("path"));
    }
    return bech32Address;
  }
}
