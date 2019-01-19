package com.samourai.whirlpool.client.tx0;

import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.test.AbstractTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tx0ServiceTest extends AbstractTest {
  private Logger log = LoggerFactory.getLogger(Tx0ServiceTest.class);

  private static final String FEE_XPUB =
      "vpub5YS8pQgZKVbrSn9wtrmydDWmWMjHrxL2mBCZ81BDp7Z2QyCgTLZCrnBprufuoUJaQu1ZeiRvUkvdQTNqV6hS96WbbVZgweFxYR1RXYkBcKt";
  private static final long FEE_VALUE = 10000; // TODO

  private Tx0Service tx0Service = new Tx0Service(params, FEE_XPUB, FEE_VALUE);

  @Test
  public void tx0() throws Exception {
    String seedWords = "all all all all all all all all all all all all";
    String passphrase = "whirlpool";
    byte[] seed = hdWalletFactory.computeSeedFromWords(seedWords);
    HD_Wallet bip84w = hdWalletFactory.getBIP84(seed, passphrase, params);

    ECKey spendFromKey = bip84w.getAccountAt(0).getChain(0).getAddressAt(61).getECKey();
    TransactionOutPoint spendFromOutpoint =
        new TransactionOutPoint(
            params,
            1,
            Sha256Hash.wrap("cc588cdcb368f894a41c372d1f905770b61ecb3fb8e5e01a97e7cedbf5e324ae"),
            Coin.valueOf(500000000));
    Bip84Wallet depositWallet = new Bip84Wallet(bip84w, 0, new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler());
    int nbOutputs = 5;
    long premixValue = 1000150;
    String feePaymentCode =
        "PM8TJXp19gCE6hQzqRi719FGJzF6AreRwvoQKLRnQ7dpgaakakFns22jHUqhtPQWmfevPQRCyfFbdDrKvrfw9oZv5PjaCerQMa3BKkPyUf9yN1CDR3w6";
    int feeSatPerByte = 1;
    IIndexHandler feeIndexHandler = new MemoryIndexHandler();
    byte[] feePayload = new byte[] {1, 2};

    Tx0 tx0 =
        tx0Service.tx0(
            spendFromKey.getPrivKeyBytes(),
            spendFromOutpoint,
            depositWallet,
            premixWallet,
            feeIndexHandler,
            feeSatPerByte,
            nbOutputs,
            premixValue,
            feePaymentCode,
            feePayload);

    Transaction tx = tx0.getTx();
    Assertions.assertEquals(nbOutputs + 3, tx.getOutputs().size()); // opReturn + fee + change

    String tx0Hash = tx.getHashAsString();
    String tx0Hex = new String(Hex.encode(tx.bitcoinSerialize()));
    log.info(tx0.getTx().toString());
    Assertions.assertEquals(
        "9a159decf30d7d34801b19b90471d146bf7b9c38e42888fe3499e13480a32090", tx0Hash);
    Assertions.assertEquals(
        "01000000000101ae24e3f5dbcee7971ae0e5b83fcb1eb67057901f2d371ca494f868b3dc8c58cc0100000000ffffffff080000000000000000426a409ae6649a7b1fc8a917f408cbf7b41e27f3a5484650aafdf5167852bd348afa8aa8213dda856188683ab187a902923e7ec3b672a6fbb637a4063c71879f68591710270000000000001600140d64011caf447917ef39b4347ee4044f96dee498d6420f00000000001600141dffe6e395c95927e4a16e8e6bd6d05604447e4dd6420f000000000016001472df8c59071778ec20264e2aeb54dd4024bcee0ad6420f00000000001600147aca3eeaecc2ffefd434c70ed67bd579e629c29dd6420f00000000001600149676ec398c2fe0736d61e09e1136958b4bf40cdad6420f0000000000160014ff715cbded0e6205a68a1f66a52ee56d56b44c814fee801d00000000160014ae076a245f1f81813e70e4f5eff2c53ab73ab97f02483045022100c79b8732b06a95ee799b95fa133bdcf52be2fd9a13606a7e946567ae123d867d0220707ab8daf0eb402d55be928d3a2b1cb713b3431e0fd1c9c48ccdfd0dd329cbd001210349baf197181fe53937d225d0e7bd14d8b5f921813c038a95d7c2648500c119b000000000",
        tx0Hex);
  }
}
