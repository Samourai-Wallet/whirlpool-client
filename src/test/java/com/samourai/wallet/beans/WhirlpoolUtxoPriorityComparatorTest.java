package com.samourai.wallet.beans;

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
  private WhirlpoolUtxoPriorityComparator c = WhirlpoolUtxoPriorityComparator.getInstance();
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
}
