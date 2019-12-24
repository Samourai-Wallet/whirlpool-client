package com.samourai.whirlpool.client.mix.handler;

import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.test.AbstractTest;
import com.samourai.whirlpool.client.tx0.*;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletAccount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bip84PostmixHandlerTest extends AbstractTest {
  private Logger log = LoggerFactory.getLogger(Bip84PostmixHandlerTest.class);

  private Bip84Wallet bip84Wallet;

  public Bip84PostmixHandlerTest() throws Exception {
    super();
    String seedWords = "all all all all all all all all all all all all";
    String passphrase = "whirlpool";
    byte[] seed = hdWalletFactory.computeSeedFromWords(seedWords);
    HD_Wallet bip84w = hdWalletFactory.getBIP84(seed, passphrase, params);
    bip84Wallet =
        new Bip84Wallet(
            bip84w,
            WhirlpoolWalletAccount.POSTMIX.getAccountIndex(),
            new MemoryIndexHandler(),
            new MemoryIndexHandler());
  }

  @Test
  public void computeNextReceiveAddressIndex() {
    Bip84PostmixHandler phCli = new Bip84PostmixHandler(bip84Wallet, false);
    Bip84PostmixHandler phMobile = new Bip84PostmixHandler(bip84Wallet, true);

    Assertions.assertEquals(0, phCli.computeNextReceiveAddressIndex());
    Assertions.assertEquals(2, phCli.computeNextReceiveAddressIndex());
    Assertions.assertEquals(4, phCli.computeNextReceiveAddressIndex());

    Assertions.assertEquals(5, phMobile.computeNextReceiveAddressIndex());
    Assertions.assertEquals(7, phMobile.computeNextReceiveAddressIndex());
    Assertions.assertEquals(9, phMobile.computeNextReceiveAddressIndex());

    Assertions.assertEquals(10, phCli.computeNextReceiveAddressIndex());
    Assertions.assertEquals(11, phMobile.computeNextReceiveAddressIndex());
    Assertions.assertEquals(12, phCli.computeNextReceiveAddressIndex());
    Assertions.assertEquals(13, phMobile.computeNextReceiveAddressIndex());
    Assertions.assertEquals(15, phMobile.computeNextReceiveAddressIndex());
    Assertions.assertEquals(16, phCli.computeNextReceiveAddressIndex());
    Assertions.assertEquals(18, phCli.computeNextReceiveAddressIndex());
  }
}
