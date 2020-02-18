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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolUtxoPriorityComparatorTest extends AbstractTest {
  private static final Logger log =
      LoggerFactory.getLogger(WhirlpoolUtxoPriorityComparatorTest.class);
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

    List<WhirlpoolUtxo> sortedUtxos = new LinkedList<WhirlpoolUtxo>(utxos);
    Collections.sort(sortedUtxos, c);
    String[] utxoStrings = toUtxoStrings(sortedUtxos);

    String[] expected =
        new String[] {
          "0.01btcPremix0conf:3",
          "0.01btcPremix10conf:3",
          "0.01btcPremix1conf:3",
          "0.01btcPremix5confError:3",
          "0.01btcPostmix0conf:3",
          "0.01btcPostmix10conf:3",
          "0.01btcPostmix1conf:3",
          "0.01btcPostmix5confError:3"
        };
    Assertions.assertArrayEquals(expected, utxoStrings);
  }

  @Test
  public void sortShuffled() {

    String[] utxoStrings1 = doSortShuffled();
    log.info("utxoStrings1=" + Arrays.toString(utxoStrings1));
    verifySortShuffled(utxoStrings1);

    String[] utxoStrings2 = doSortShuffled();
    log.info("utxoStrings2=" + Arrays.toString(utxoStrings2));
    verifySortShuffled(utxoStrings2);

    Assertions.assertFalse(Arrays.equals(utxoStrings1, utxoStrings2));
  }

  private String[] doSortShuffled() {
    WhirlpoolUtxoPriorityComparator c =
        new WhirlpoolUtxoPriorityComparator(
            new HashSet<String>(), new LinkedHashMap<String, Integer>());

    List<WhirlpoolUtxo> sortedUtxos = new LinkedList<WhirlpoolUtxo>(utxos);
    c.sortShuffled(sortedUtxos);
    return toUtxoStrings(sortedUtxos);
  }

  private void verifySortShuffled(String[] utxoStrings) {
    // expected: first premix
    for (int i = 0; i < 4; i++) {
      Assertions.assertTrue(utxoStrings[i].contains("Premix"));
    }
    Assertions.assertEquals(utxoStrings[3], "0.01btcPremix5confError:3"); // error last

    // expected: then postmix
    for (int i = 4; i < utxoStrings.length; i++) {
      Assertions.assertTrue(utxoStrings[i].contains("Postmix"));
    }
    Assertions.assertEquals(
        utxoStrings[utxoStrings.length - 1], "0.01btcPostmix5confError:3"); // error last
  }

  private String[] toUtxoStrings(Collection<WhirlpoolUtxo> utxos) {
    return StreamSupport.stream(utxos)
        .map(
            new Function<WhirlpoolUtxo, String>() {
              @Override
              public String apply(WhirlpoolUtxo whirlpoolUtxo) {
                return whirlpoolUtxo.getUtxo().tx_hash + ":" + whirlpoolUtxo.getUtxo().tx_output_n;
              }
            })
        .collect(Collectors.<String>toList())
        .toArray(new String[] {});
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
