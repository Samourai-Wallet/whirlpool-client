package com.samourai.whirlpool.client.test;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

public class AbstractTest {
  protected NetworkParameters params = TestNet3Params.get();
  protected HD_WalletFactoryJava hdWalletFactory = HD_WalletFactoryJava.getInstance();
  protected Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();

  protected UnspentResponse.UnspentOutput newUnspentOutput(String hash, int index, long value) {
    UnspentResponse.UnspentOutput spendFrom = new UnspentResponse.UnspentOutput();
    spendFrom.tx_hash = hash;
    spendFrom.tx_output_n = index;
    spendFrom.value = value;
    spendFrom.script = "foo";
    spendFrom.addr = "foo";
    spendFrom.confirmations = 1234;
    spendFrom.xpub = new UnspentResponse.UnspentOutput.Xpub();
    spendFrom.xpub.path = "foo";
    return spendFrom;
  }
}
