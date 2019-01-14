package com.samourai.whirlpool.client.test;

import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

public class AbstractTest {
  protected NetworkParameters params = TestNet3Params.get();
  protected HD_WalletFactoryJava hdWalletFactory = HD_WalletFactoryJava.getInstance();
  protected Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
}
