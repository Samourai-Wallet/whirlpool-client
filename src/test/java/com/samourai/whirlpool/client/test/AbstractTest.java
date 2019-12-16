package com.samourai.whirlpool.client.test;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

public class AbstractTest {
  protected NetworkParameters params = TestNet3Params.get();
  protected HD_WalletFactoryJava hdWalletFactory = HD_WalletFactoryJava.getInstance();
  protected Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  protected Pool pool01btc;

  public AbstractTest() {
    pool01btc = new Pool();
    pool01btc.setPoolId("0.1btc");
    pool01btc.setDenomination(1000000);
    pool01btc.setFeeValue(50000);
    pool01btc.setMinMustMix(3);
    pool01btc.setMustMixBalanceMin(1000170);
    pool01btc.setMustMixBalanceCap(1009500);
    pool01btc.setMustMixBalanceMax(1010000);
    pool01btc.setMinAnonymitySet(5);
    pool01btc.setMinMustMix(3);
    pool01btc.setNbRegistered(0);
    pool01btc.setMixAnonymitySet(5);
    pool01btc.setMixStatus(MixStatus.CONFIRM_INPUT);
    pool01btc.setElapsedTime(1000);
    pool01btc.setNbConfirmed(0);
  }

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
