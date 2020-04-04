package com.samourai.whirlpool.client.wallet.orchestrator;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.test.AbstractTest;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import java.util.*;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixOrchestratorTest extends AbstractTest {
  private static final Logger log = LoggerFactory.getLogger(MixOrchestratorTest.class);
  private MixOrchestratorData data;
  private MixOrchestrator mixOrchestrator;
  private MixingStateEditable mixingState = new MixingStateEditable(false);
  private List<WhirlpoolUtxo> utxos = new LinkedList<WhirlpoolUtxo>();
  private List<WhirlpoolUtxo> mixingHistory = new LinkedList<WhirlpoolUtxo>();
  private String POOL_001;

  @BeforeEach
  public void setUp() throws Exception {
    POOL_001 = pool001btc.getPoolId();
  }

  protected void init(Integer maxClients, int maxClientsPerPool) throws Exception {
    utxos = new LinkedList<WhirlpoolUtxo>();
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.PREMIX, "0.01btcPremix0conf", 0, null));
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.PREMIX, "0.01btcPremix10conf", 10, null));
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.PREMIX, "0.01btcPremix5confError", 5, 3L));
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.PREMIX, "0.01btcPremix1conf", 1, null));

    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.POSTMIX, "0.01btcPostmix0conf", 0, null));
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.POSTMIX, "0.01btcPostmix10conf", 10, null));
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.POSTMIX, "0.01btcPostmix5confError", 5, 3L));
    utxos.add(newUtxo(POOL_001, WhirlpoolAccount.POSTMIX, "0.01btcPostmix1conf", 1, null));

    data =
        new MixOrchestratorData(mixingState) {
          @Override
          public Stream<WhirlpoolUtxo> getQueue() {
            return StreamSupport.stream(utxos);
          }

          @Override
          public Collection<Pool> getPools() throws Exception {
            return MixOrchestratorTest.this.getPools();
          }
        };

    mixOrchestrator =
        new MixOrchestrator(999999, 0, data, maxClients, maxClientsPerPool, true, 99) {
          @Override
          protected WhirlpoolClient runWhirlpoolClient(
              WhirlpoolUtxo whirlpoolUtxo, WhirlpoolClientListener listener) {
            mixingHistory.add(whirlpoolUtxo);
            WhirlpoolClient whirlpoolClient =
                WhirlpoolClientImpl.newClient(null); // don't run for test
            ((WhirlpoolClientImpl) whirlpoolClient)._setListener(listener);
            return whirlpoolClient;
          }

          @Override
          protected void stopWhirlpoolClient(Mixing mixing, boolean cancel, boolean reQueue) {
            ((WhirlpoolClientImpl) mixing.getWhirlpoolClient())
                .getListener()
                .fail(MixFailReason.CANCEL, "");
            if (reQueue) {
              try {
                mixQueue(mixing.getUtxo());
              } catch (Exception e) {
                log.error("", e);
              }
            }
          }
        };

    WhirlpoolUtxoChanges whirlpoolUtxoChanges = new WhirlpoolUtxoChanges(true);
    whirlpoolUtxoChanges.getUtxosDetected().addAll(utxos);
    mixOrchestrator.onUtxoChanges(whirlpoolUtxoChanges);

    Thread.sleep(600);
    log.debug("// --- init complete ---");
  }

  @AfterEach
  public void tearDown() {
    log.debug("// --- tearDown ---");
    mixOrchestrator.stop();
  }

  @Test
  public void testMixOrder() throws Exception {
    init(99, 99);

    // check order
    String[] utxoStrings1 = doTestMixOrder();
    String[] utxoStrings2 = doTestMixOrder();

    // random utxo selection
    Assertions.assertEquals(6, utxoStrings1.length);
    Assertions.assertEquals(6, utxoStrings2.length);
  }

  private String[] doTestMixOrder() throws Exception {
    findAndMixAll();
    String utxoStrings[] = toUtxoStrings(mixingHistory);
    log.info("### utxoStrings=" + Arrays.toString(utxoStrings));
    verifySortShuffled(utxoStrings);
    mixOrchestrator.stop();
    mixingHistory.clear();
    return utxoStrings;
  }

  private void findAndMixAll() throws Exception {
    mixOrchestrator.start(false);
    boolean found;
    do {
      found = mixOrchestrator.findAndMix();
      Thread.sleep(300);
    } while (found);
  }

  @Test
  public void testSpendConfirmDetect() throws Exception {
    init(99, 99);

    // mix all
    findAndMixAll();
    Assertions.assertEquals(6, mixingHistory.size());

    // spend "0.01btcPremix10conf"
    WhirlpoolUtxo utxo = utxos.get(1);
    Assertions.assertNotNull(data.getMixing(utxo.getUtxo())); // mixing
    {
      WhirlpoolUtxoChanges whirlpoolUtxoChanges = new WhirlpoolUtxoChanges(false);
      whirlpoolUtxoChanges.getUtxosRemoved().add(utxos.get(1));
      mixOrchestrator.onUtxoChanges(whirlpoolUtxoChanges);
    }

    // => should stop mixing
    Assertions.assertNull(data.getMixing(utxo.getUtxo())); // not mixing

    // confirm "0.01btcPostmix0conf"
    utxo = utxos.get(0);
    Assertions.assertNull(data.getMixing(utxo.getUtxo())); // not mixing
    utxo.getUtxo().confirmations = 7;
    {
      WhirlpoolUtxoChanges whirlpoolUtxoChanges = new WhirlpoolUtxoChanges(false);
      whirlpoolUtxoChanges.getUtxosUpdated().add(utxo);
      mixOrchestrator.onUtxoChanges(whirlpoolUtxoChanges);
    }
    Thread.sleep(300);

    // => shoud start mixing
    Assertions.assertNotNull(data.getMixing(utxo.getUtxo())); // mixing

    // new utxo
    utxo = newUtxo(POOL_001, WhirlpoolAccount.PREMIX, "0.01btcPremix9confNew", 9, null);
    utxos.add(utxo);
    Assertions.assertNull(data.getMixing(utxo.getUtxo())); // not mixing
    {
      WhirlpoolUtxoChanges whirlpoolUtxoChanges = new WhirlpoolUtxoChanges(false);
      whirlpoolUtxoChanges.getUtxosDetected().add(utxo);
      mixOrchestrator.onUtxoChanges(whirlpoolUtxoChanges);
    }
    Thread.sleep(300);

    // => shoud start mixing
    Assertions.assertNotNull(data.getMixing(utxo.getUtxo())); // mixing
  }

  @Test
  public void testMaxClients1() throws Exception {
    init(1, 1);

    // mix all
    findAndMixAll();

    // just 1 mixing
    Assertions.assertEquals(1, mixingHistory.size());

    // stop current mix on utxo spend
    WhirlpoolUtxo utxo = mixingHistory.get(0);
    Assertions.assertNotNull(data.getMixing(utxo.getUtxo())); // mixing
    utxos.remove(utxo);
    {
      WhirlpoolUtxoChanges whirlpoolUtxoChanges = new WhirlpoolUtxoChanges(false);
      whirlpoolUtxoChanges.getUtxosRemoved().add(utxo);
      mixOrchestrator.onUtxoChanges(whirlpoolUtxoChanges);
    }
    Thread.sleep(600);

    // => should stop mixing
    Assertions.assertNull(data.getMixing(utxo.getUtxo())); // not mixing

    // another one is mixing
    Assertions.assertEquals(2, mixingHistory.size());
  }

  @Test
  public void testMaxClientsPerPool1() throws Exception {
    init(99, 1);

    // mix all
    findAndMixAll();

    // just 1 mixing per pool
    Assertions.assertEquals(1, mixingHistory.size());
    String utxoStrings[] = toUtxoStrings(mixingHistory);

    // adding same priority utxo doesn't change anything
    WhirlpoolUtxo newUtxo =
        newUtxo(POOL_001, WhirlpoolAccount.PREMIX, "0.01btcPremix10confNew", 10, null);
    utxos.add(newUtxo);
    {
      WhirlpoolUtxoChanges whirlpoolUtxoChanges = new WhirlpoolUtxoChanges(false);
      whirlpoolUtxoChanges.getUtxosDetected().add(newUtxo);
      mixOrchestrator.onUtxoChanges(whirlpoolUtxoChanges);
    }
    // => nothing changed
    Assertions.assertEquals(1, mixingHistory.size());
    Assertions.assertTrue(Arrays.equals(utxoStrings, toUtxoStrings(mixingHistory)));

    // adding same priority with mixNow() get mixed directly
    mixOrchestrator.mixNow(newUtxo);
    // => mixing changed
    Assertions.assertEquals(2, mixingHistory.size());
    utxoStrings = toUtxoStrings(mixingHistory);
    Assertions.assertEquals("0.01btcPremix10confNew:3", utxoStrings[1]);
  }

  private void verifySortShuffled(String[] utxoStrings) {
    // first premix
    for (int i = 0; i < 2; i++) {
      Assertions.assertTrue(utxoStrings[i].contains("Premix"));
    }
    Assertions.assertEquals("0.01btcPremix5confError:3", utxoStrings[2]); // error last

    // then postmix
    for (int i = 3; i < 5; i++) {
      Assertions.assertTrue(utxoStrings[i].contains("Postmix"));
    }
    Assertions.assertEquals("0.01btcPostmix5confError:3", utxoStrings[5]); // postmix error last

    // non-confirmated cannot be mixed
    Assertions.assertFalse(ArrayUtils.contains(utxoStrings, "0.01btcPremix0conf"));
    Assertions.assertFalse(ArrayUtils.contains(utxoStrings, "0.01btcPostmix0conf"));
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
