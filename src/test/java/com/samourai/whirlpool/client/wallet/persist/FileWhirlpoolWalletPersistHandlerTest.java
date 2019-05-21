package com.samourai.whirlpool.client.wallet.persist;

import com.samourai.api.client.SamouraiApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.client.Bip84ApiWallet;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.test.AbstractTest;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletAccount;
import java.io.File;
import java.util.List;
import java8.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileWhirlpoolWalletPersistHandlerTest extends AbstractTest {

  private FileWhirlpoolWalletPersistHandler persistHandler;
  private WhirlpoolWallet whirlpoolWallet;
  private File fileState;
  private File fileUtxos;

  @BeforeEach
  public void setup() throws Exception {
    fileState = new File("/tmp/state");
    if (fileState.exists()) {
      fileState.delete();
    }

    fileUtxos = new File("/tmp/utxos");
    if (fileUtxos.exists()) {
      fileUtxos.delete();
    }

    this.persistHandler = new FileWhirlpoolWalletPersistHandler(fileState, fileUtxos);
    persistHandler.setInitialized(true);

    this.whirlpoolWallet = computeWallet(new SamouraiApi(null, BackendServer.TESTNET));
  }

  private void reload() {
    ((FileWhirlpoolWalletPersistHandler) whirlpoolWallet.getConfig().getPersistHandler())
        .getUtxoConfigHandler()
        .loadUtxoConfigs(whirlpoolWallet);
  }

  @Test
  public void testCleanup() throws Exception {
    // save

    UnspentOutput utxoFoo = new UnspentOutput();
    utxoFoo.tx_output_n = 1;
    utxoFoo.tx_hash = "foo";
    WhirlpoolUtxo foo = new WhirlpoolUtxo(utxoFoo, null, null, whirlpoolWallet);
    foo.getUtxoConfig().setMixsTarget(1);

    UnspentOutput utxoBar = new UnspentOutput();
    utxoBar.tx_output_n = 2;
    utxoBar.tx_hash = "bar";
    WhirlpoolUtxo bar = new WhirlpoolUtxo(utxoBar, null, null, whirlpoolWallet);
    bar.getUtxoConfig().setMixsTarget(2);

    // verify
    Assertions.assertNull(persistHandler.getUtxoConfig("foo"));
    Assertions.assertNull(persistHandler.getUtxoConfig("foo", 2));
    Assertions.assertEquals(1, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());
    Assertions.assertEquals(2, persistHandler.getUtxoConfig("bar", 2).getMixsTarget());
    Assertions.assertNull(persistHandler.getUtxoConfig("bar", 1));

    persistHandler.save();

    // re-read
    reload();

    // verify
    Assertions.assertNull(persistHandler.getUtxoConfig("foo"));
    Assertions.assertNull(persistHandler.getUtxoConfig("foo", 2));
    Assertions.assertEquals(1, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());
    Assertions.assertEquals(2, persistHandler.getUtxoConfig("bar", 2).getMixsTarget());
    Assertions.assertNull(persistHandler.getUtxoConfig("bar", 1));

    // first clean => unchanged
    List<WhirlpoolUtxo> knownUtxos = Lists.of(foo);
    persistHandler.cleanUtxoConfig(knownUtxos);

    // verify
    Assertions.assertNull(persistHandler.getUtxoConfig("foo"));
    Assertions.assertNull(persistHandler.getUtxoConfig("foo", 2));
    Assertions.assertEquals(1, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());
    Assertions.assertEquals(2, persistHandler.getUtxoConfig("bar", 2).getMixsTarget());
    Assertions.assertNull(persistHandler.getUtxoConfig("bar", 1));

    // second clean => "bar" removed
    persistHandler.cleanUtxoConfig(knownUtxos);

    // verify
    Assertions.assertNull(persistHandler.getUtxoConfig("foo"));
    Assertions.assertNull(persistHandler.getUtxoConfig("foo", 2));
    Assertions.assertEquals(1, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());
    Assertions.assertNull(persistHandler.getUtxoConfig("bar", 2));
    Assertions.assertNull(persistHandler.getUtxoConfig("bar", 1));

    // modify foo
    foo.getUtxoConfig().setMixsTarget(5);
    Assertions.assertEquals(5, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());
    persistHandler.save();

    // verify
    Assertions.assertEquals(5, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());

    // re-read
    reload();
    Assertions.assertEquals(5, persistHandler.getUtxoConfig("foo", 1).getMixsTarget());
  }

  private WhirlpoolWallet computeWallet(SamouraiApi samouraiApi) throws Exception {
    byte[] seed =
        hdWalletFactory.computeSeedFromWords("all all all all all all all all all all all all");
    HD_Wallet bip84w = hdWalletFactory.getBIP84(seed, "foo", params);
    Bip84ApiWallet depositWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.DEPOSIT.getAccountIndex(),
            new MemoryIndexHandler(1),
            new MemoryIndexHandler(1),
            samouraiApi,
            false,
            1,
            1);
    Bip84ApiWallet premixWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.PREMIX.getAccountIndex(),
            new MemoryIndexHandler(1),
            new MemoryIndexHandler(1),
            samouraiApi,
            false,
            1,
            1);
    Bip84ApiWallet postmixWallet =
        new Bip84ApiWallet(
            bip84w,
            WhirlpoolWalletAccount.POSTMIX.getAccountIndex(),
            new MemoryIndexHandler(1),
            new MemoryIndexHandler(1),
            samouraiApi,
            false,
            1,
            1);
    WhirlpoolWalletConfig config =
        new WhirlpoolWalletConfig(
            null,
            null,
            persistHandler,
            WhirlpoolServer.LOCAL_TESTNET.getServerUrl(),
            WhirlpoolServer.LOCAL_TESTNET);
    return new WhirlpoolWalletService()
        .openWallet(config, depositWallet, premixWallet, postmixWallet);
  }
}
