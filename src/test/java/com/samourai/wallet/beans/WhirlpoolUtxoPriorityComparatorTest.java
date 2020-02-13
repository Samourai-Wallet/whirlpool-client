package com.samourai.wallet.beans;

import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.whirlpool.client.test.AbstractTest;
import com.samourai.whirlpool.client.wallet.beans.*;
import java.util.*;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WhirlpoolUtxoPriorityComparatorTest extends AbstractTest {
  private List<WhirlpoolUtxo> utxos = new LinkedList<WhirlpoolUtxo>();

  public WhirlpoolUtxoPriorityComparatorTest() {
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.PREMIX, "0.01btcPremix0conf", 0, null));
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.PREMIX, "0.01btcPremix10conf", 10, null));
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.PREMIX, "0.01btcPremix5confError", 5, 3L));
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.PREMIX, "0.01btcPremix1conf", 1, null));

    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.POSTMIX, "0.01btcPostmix0conf", 0, null));
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.POSTMIX, "0.01btcPostmix10conf", 10, null));
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.POSTMIX, "0.01btcPostmix5confError", 5, 3L));
    utxos.add(newUtxo("0.01btc", WhirlpoolAccount.POSTMIX, "0.01btcPostmix1conf", 1, null));
  }

  @Test
  public void sortNoMixing() throws Exception {
    WhirlpoolUtxoPriorityComparator c =
        new WhirlpoolUtxoPriorityComparator(
            new HashSet<String>(), new LinkedHashMap<String, Integer>());
    String[] sortedUtxos =
        StreamSupport.stream(utxos)
            .sorted(c)
            .map(
                new Function<WhirlpoolUtxo, String>() {
                  @Override
                  public String apply(WhirlpoolUtxo whirlpoolUtxo) {
                    return whirlpoolUtxo.getUtxo().tx_hash
                        + ":"
                        + whirlpoolUtxo.getUtxo().tx_output_n;
                  }
                })
            .collect(Collectors.<String>toList())
            .toArray(new String[] {});

    // expected: first premix
    for (int i = 0; i < 4; i++) {
      Assertions.assertTrue(sortedUtxos[i].contains("Premix"));
    }
    Assertions.assertEquals(sortedUtxos[3], "0.01btcPremix5confError:3"); // error last

    // expected: then postmix
    for (int i = 4; i < sortedUtxos.length; i++) {
      Assertions.assertTrue(sortedUtxos[i].contains("Postmix"));
    }
    Assertions.assertEquals(
        sortedUtxos[sortedUtxos.length - 1], "0.01btcPostmix5confError:3"); // error last
  }

  private WhirlpoolUtxo newUtxo(
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
