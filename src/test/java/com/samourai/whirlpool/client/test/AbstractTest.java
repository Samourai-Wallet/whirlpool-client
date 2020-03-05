package com.samourai.whirlpool.client.test;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.java.HD_WalletFactoryJava;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import java.util.Collection;
import java8.util.Lists;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;

public class AbstractTest {
  protected NetworkParameters params = TestNet3Params.get();
  protected HD_WalletFactoryJava hdWalletFactory = HD_WalletFactoryJava.getInstance();
  protected Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  protected Pool pool01btc;
  protected Pool pool05btc;
  protected Pool pool001btc;

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

    pool001btc = new Pool();
    pool001btc.setPoolId("0.01btc");
    pool001btc.setDenomination(100000);
    pool001btc.setFeeValue(5000);
    pool001btc.setMinMustMix(3);
    pool001btc.setMustMixBalanceMin(100017);
    pool001btc.setMustMixBalanceCap(100950);
    pool001btc.setMustMixBalanceMax(101000);
    pool001btc.setMinAnonymitySet(5);
    pool001btc.setMinMustMix(3);
    pool001btc.setNbRegistered(0);
    pool001btc.setMixAnonymitySet(5);
    pool001btc.setMixStatus(MixStatus.CONFIRM_INPUT);
    pool001btc.setElapsedTime(1000);
    pool001btc.setNbConfirmed(0);

    pool05btc = new Pool();
    pool05btc.setPoolId("0.5btc");
    pool05btc.setDenomination(5000000);
    pool05btc.setFeeValue(250000);
    pool05btc.setMinMustMix(3);
    pool05btc.setMustMixBalanceMin(5000170);
    pool05btc.setMustMixBalanceCap(5009500);
    pool05btc.setMustMixBalanceMax(5010000);
    pool05btc.setMinAnonymitySet(5);
    pool05btc.setMinMustMix(3);
    pool05btc.setNbRegistered(0);
    pool05btc.setMixAnonymitySet(5);
    pool05btc.setMixStatus(MixStatus.CONFIRM_INPUT);
    pool05btc.setElapsedTime(1000);
    pool05btc.setNbConfirmed(0);
  }

  protected Collection<Pool> getPools() {
    return Lists.of(pool001btc, pool01btc, pool05btc);
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

  protected WhirlpoolUtxo newUtxo(
      String poolId, WhirlpoolAccount whirlpoolAccount, String hash, int confirms, Long lastError) {
    UnspentResponse.UnspentOutput utxo = newUnspentOutput(hash, 3, 100L);
    utxo.confirmations = confirms;
    WhirlpoolUtxoConfig utxoConfig =
        new WhirlpoolUtxoConfig(poolId, 5, 0, System.currentTimeMillis());
    WhirlpoolUtxo whirlpoolUtxo =
        new WhirlpoolUtxo(utxo, whirlpoolAccount, utxoConfig, WhirlpoolUtxoStatus.READY);
    whirlpoolUtxo.getUtxoState().setLastError(lastError);
    return whirlpoolUtxo;
  }
}
