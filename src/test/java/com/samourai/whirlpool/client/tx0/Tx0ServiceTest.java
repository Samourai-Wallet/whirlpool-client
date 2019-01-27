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
  public void tx0_5premix_withChange() throws Exception {
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
            Coin.valueOf(500000000)); // large balance
    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    int nbOutputsPreferred = 10;
    int nbOutputsExpected = 10;
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
            nbOutputsPreferred,
            premixValue,
            feePaymentCode,
            feePayload);

    Transaction tx = tx0.getTx();
    Assertions.assertEquals(
        nbOutputsExpected + 3, tx.getOutputs().size()); // opReturn + fee + change

    String tx0Hash = tx.getHashAsString();
    String tx0Hex = new String(Hex.encode(tx.bitcoinSerialize()));
    log.info(tx0.getTx().toString());
    Assertions.assertEquals(
        "42799061dfdf62935c34a787eba29b879112aa1effd0191a81cc8864eaeae442", tx0Hash);
    Assertions.assertEquals(
        "01000000000101ae24e3f5dbcee7971ae0e5b83fcb1eb67057901f2d371ca494f868b3dc8c58cc0100000000ffffffff0d0000000000000000426a409ae6649a7b1fc8a917f408cbf7b41e27f3a5484650aafdf5167852bd348afa8aa8213dda856188683ab187a902923e7ec3b672a6fbb637a4063c71879f6859171027000000000000160014f6a884f18f4d7e78a4167c3e56773c3ae58e0164d6420f00000000001600141dffe6e395c95927e4a16e8e6bd6d05604447e4dd6420f00000000001600142540e8d450b7114a8b0b429709508735b4b1bbfbd6420f00000000001600145b1cdb2e6ae13f98034b84957d9e0975ad7e6da5d6420f000000000016001472df8c59071778ec20264e2aeb54dd4024bcee0ad6420f00000000001600147aca3eeaecc2ffefd434c70ed67bd579e629c29dd6420f0000000000160014833e54dd2bdc90a6d92aedbecef1ca9cdb24a4c4d6420f00000000001600148535df3b314d3191037e38c698ddb6bac83ba95ad6420f00000000001600149676ec398c2fe0736d61e09e1136958b4bf40cdad6420f0000000000160014adb93750e1ffcfcefc54c6be67bd3011878a5aa5d6420f0000000000160014ff715cbded0e6205a68a1f66a52ee56d56b44c810f9f341d000000001600141bd05eb7c9cb516fddd8187cecb2e0cb4e21ac8702483045022100e994c56ba75ef4cb24834a73b23b94b293e3ffe992a1d87bce39a0149899b45c022004a9180da0ee6c8ef95e2ec956b40cba74220d76a9d97266a774c0609396ebcc01210349baf197181fe53937d225d0e7bd14d8b5f921813c038a95d7c2648500c119b000000000",
        tx0Hex);
  }

  @Test
  public void tx0_maxpremix_withChange() throws Exception {
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
            Coin.valueOf(500000000)); // large balance
    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    int nbOutputsPreferred = 999999;
    int nbOutputsExpected = Tx0Service.NB_PREMIX_MAX;
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
            nbOutputsPreferred,
            premixValue,
            feePaymentCode,
            feePayload);

    Transaction tx = tx0.getTx();
    Assertions.assertEquals(
        nbOutputsExpected + 3, tx.getOutputs().size()); // opReturn + fee + change

    String tx0Hash = tx.getHashAsString();
    String tx0Hex = new String(Hex.encode(tx.bitcoinSerialize()));
    log.info(tx0.getTx().toString());
    Assertions.assertEquals(
        "a3eb16a313d890a2524d39b93428713005c89b0f2cfe5ab7956fc7e6b207ebcb", tx0Hash);
    Assertions.assertEquals(
        "01000000000101ae24e3f5dbcee7971ae0e5b83fcb1eb67057901f2d371ca494f868b3dc8c58cc0100000000ffffffff670000000000000000426a409ae6649a7b1fc8a917f408cbf7b41e27f3a5484650aafdf5167852bd348afa8aa8213dda856188683ab187a902923e7ec3b672a6fbb637a4063c71879f6859171027000000000000160014f6a884f18f4d7e78a4167c3e56773c3ae58e0164d6420f000000000016001401ddf76f209c3c5a3edd4d69fe129627dc379ec7d6420f00000000001600140774c98855e76c63d2992700ef0f676e90e718c7d6420f00000000001600140892f2617945684132af0c60eb1280a45d7ccb93d6420f00000000001600140bf7e6b30d37af8340f839368ab2a6539ff60efbd6420f000000000016001411dd9e6c1ff0b5539c22aaac5291bd19b928041fd6420f000000000016001411f20dc2186828e015c75cf9ff93a120d6888ea9d6420f00000000001600141205162cea5285f889b8d33bb264efc9dbbf2a5ad6420f0000000000160014131bb0ffed0409ce807a0edb7c1eb2f2b54a44e4d6420f00000000001600141600b76ad001260a9d695c3a4ddac0b816f5f14ed6420f00000000001600141690c620153b1d8673ab15810b4a005b49688aa8d6420f00000000001600141978176eaf2832b4fdf3d4a5fddd93262844fbc6d6420f00000000001600141a4f5855d874fb6553264a605f86692b00e0d4fad6420f00000000001600141beac2ed90b0ba744ceb15214d543a380218f0b9d6420f00000000001600141dffe6e395c95927e4a16e8e6bd6d05604447e4dd6420f00000000001600141e19ce4c45b4b25d65b44aaead6a59d7fcca2c1fd6420f000000000016001420da1abda62e9e9c287747fd3d9d715141273ebcd6420f0000000000160014238e0ea04a88cae5cefd06220cc069915e2b4062d6420f000000000016001423a8abe034ce6e86c05d500c7eaeab4af1b9a620d6420f0000000000160014248cb9a55d8ecc205f8fd6fbbb109d0aa6a0be5bd6420f00000000001600142540e8d450b7114a8b0b429709508735b4b1bbfbd6420f0000000000160014262db5d0e67310eed49779d3e2dfadfa0685ac9ad6420f000000000016001429d3c169a33204cdfafdab1b49430d8d327cd263d6420f0000000000160014314e8149b9f38f80fa984525fdbd9eeb086556abd6420f00000000001600143617a2707bbcb90d5d5c3755ccd7345850d62497d6420f0000000000160014372e2211f7c3621f1a2b348905445a0eb38c6b55d6420f00000000001600143d48125b6e51bdf8803d10d10250c34f5867dd51d6420f0000000000160014408f78c45c889b9d5fdaa120567379599480c57cd6420f000000000016001441eda3bd7b764345efbbb3057b87b2d9c7eff837d6420f000000000016001442ac3c3b0ccb3ce5fa5fb15030debd64de6f18f5d6420f000000000016001446607ecdc585d7f65ef24a77cedb639dd2c9fd3ed6420f0000000000160014474392b1a70c12525969836b843235adab29d07bd6420f00000000001600144a068b6084db2f29a63537c076e1420d677009dcd6420f000000000016001450360db5c57c66c14cb4c1a325a3a95f2f657115d6420f0000000000160014585e84212f4e8ab85febcb04c42b25f9b2e5abd8d6420f000000000016001458be34efb6a736ee543df07b1ba39795faf94073d6420f00000000001600145b1cdb2e6ae13f98034b84957d9e0975ad7e6da5d6420f00000000001600145bfa07b8250d3748a19a052e460edf43d8a7b5e3d6420f00000000001600145c0e86994fb9632bd5aa66e8b2ee2748202dd031d6420f00000000001600145c336ba5c774f31da50f5659cc623dd7bd65e54dd6420f0000000000160014602feb7910d5c5e2afea07a992cad2a0375dcd14d6420f000000000016001460426c77a8dbf4f2eb43e256f0cb31f61aca0e7fd6420f00000000001600146081e8072529abc280639f74b79723acefc265abd6420f000000000016001461b350784c7a76a0d54fe98e9aa7ddd417a176dad6420f0000000000160014635544736286b6ce83b0d1dcead802798e8793c3d6420f00000000001600146ed65a10b885fa58d6fefe8d36a61f93f88a050dd6420f000000000016001472df8c59071778ec20264e2aeb54dd4024bcee0ad6420f00000000001600147336b9ac8626c93da06531fafc0db4dab00de2e1d6420f000000000016001473dac72c581809f20123fbbcf0caae3cb60f6c94d6420f0000000000160014750293fc6221a5761dd8640221f111725b4cd721d6420f00000000001600147a05b67f6ca96d0a38050b8b88c5f19606923387d6420f00000000001600147aca3eeaecc2ffefd434c70ed67bd579e629c29dd6420f0000000000160014822372db51dcdc04fe2971db34e0c2c1cdebee7bd6420f0000000000160014833e54dd2bdc90a6d92aedbecef1ca9cdb24a4c4d6420f00000000001600148535df3b314d3191037e38c698ddb6bac83ba95ad6420f00000000001600148800a61bdd0c3e7bcec6f8a81785cd240426664fd6420f00000000001600148b2856700010fff72a64e0e7093b92eeed726bbfd6420f0000000000160014924a8e48b305d06d6d6c8508694dacbc556fb22ad6420f00000000001600149676ec398c2fe0736d61e09e1136958b4bf40cdad6420f0000000000160014983aca96e4411ca7e31b7feb54c4a1c809592ccad6420f000000000016001499a4f9e271bd3bd8971c5ebe207b58b1b74319f5d6420f000000000016001499a51bd888f73af6a1dcbe01cc6e080f97b950f0d6420f00000000001600149ebdb1244c3d7b071ac7356a1f7a47a0b7096ad0d6420f00000000001600149ecfbbe9c2c7b60ee8dd2db4a6b910a8f57f1501d6420f0000000000160014a0f7239dab89a0d829844346213a44778fce5d56d6420f0000000000160014a42db9573f9cc15f5d2b4fc04c392d9d73b93c4ad6420f0000000000160014a437c2400b0cac5c3b150a4566c87268e3c87c59d6420f0000000000160014a820faa41563c6a17cbcb11e999f368b5725a5ead6420f0000000000160014a94bb2625322633651667f066558e8801f22234fd6420f0000000000160014adb93750e1ffcfcefc54c6be67bd3011878a5aa5d6420f0000000000160014ade55072356f2b40a66f28bc1321f60e2cbf1d9cd6420f0000000000160014ae97253d4bcd3378af434a9fe58aa98d1458af8ad6420f0000000000160014af903912608a3b93f72df89f51db3588850f2da5d6420f0000000000160014b21c13325c88e5357ad841885533c69628e8eae1d6420f0000000000160014b39a130d600fb6b01108c8eeb45bc5ab59add6e2d6420f0000000000160014b4c878dca1c29556ba62439c3ea63da265fdad1cd6420f0000000000160014b75a8e3985738c8a0d4fe3d753003b065af5f075d6420f0000000000160014c33a425a0f0064ab20321a4483f0df12893eca71d6420f0000000000160014c96e5a86b3535d9bbff3a10943333fca30540457d6420f0000000000160014caddb4ed988633ea3f87f11a5644ec9d3a4d5b6dd6420f0000000000160014cff5fd8f452d8096fc0a13115b147174c71d6ddbd6420f0000000000160014d0f18cba83af66632f75402d4987f49301d77b48d6420f0000000000160014d91e5378da1a95bf3685d18bac9ebe79a182d3b2d6420f0000000000160014daf8e7896e1e76379a4f7f48b9b97a1fb5709b26d6420f0000000000160014def20b6beee601ff2c4e6da931996d54faeb7ad8d6420f0000000000160014e26d65ddd4f8b9a5a5edbbd98c9e14308fcc195cd6420f0000000000160014e7e68ed2440cbaf05e9d100addf1f7d62be8b7b6d6420f0000000000160014ea7f0c80100d858019c0492c6cae9795c6a8c53cd6420f0000000000160014ead26d39d8a04d41956a552041aae9898c38b690d6420f0000000000160014ebd6d811893a9fbbc9b7b430747f4410934d14abd6420f0000000000160014ec3b40fc78983fcd811c88aea23a567bfc32fe67d6420f0000000000160014ec795d36ce08ce8ae3d2244bb989e0781b989528d6420f0000000000160014f11b0fc3d9f8956e15ce5696fe7b92b4e1b6fc41d6420f0000000000160014f1a62d55bb79805a024ce4ce0eb3ea616dc3ffa1d6420f0000000000160014f418fa504f78d2c6efb26705ee9337497284cd00d6420f0000000000160014f4d048354783d85f1b3f6e9a196cf06a749760c5d6420f0000000000160014f8c64d20de88af1e9fb85f10086b30069a271302d6420f0000000000160014fa4e11decb556495658fc02b562d39e4a707aa76d6420f0000000000160014fcdaad7adadbf70ac5bfbb5a2a7964a41df0e33dd6420f0000000000160014fceca503378f76facd722a45e4fdb6799a24f372d6420f0000000000160014ff715cbded0e6205a68a1f66a52ee56d56b44c813914d717000000001600141bd05eb7c9cb516fddd8187cecb2e0cb4e21ac870247304402206b49787b31a7461dcd70079f85cc04e64a1257a370d9db5828e3780f2685ce54022015b03c56d4fa9099c2c63b6d9dee1d8ad908015f38758dec1daa2c5a222352a201210349baf197181fe53937d225d0e7bd14d8b5f921813c038a95d7c2648500c119b000000000",
        tx0Hex);
  }

  @Test
  public void tx0_1premix_noChange() throws Exception {
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
            Coin.valueOf(1010498)); // exact balance
    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    int nbOutputsPreferred = 5;
    int nbOutputsExpected = 1;
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
            nbOutputsPreferred,
            premixValue,
            feePaymentCode,
            feePayload);

    Transaction tx = tx0.getTx();
    Assertions.assertEquals(
        nbOutputsExpected + 2, tx.getOutputs().size()); // opReturn + fee (no change)

    String tx0Hash = tx.getHashAsString();
    String tx0Hex = new String(Hex.encode(tx.bitcoinSerialize()));
    log.info(tx0.getTx().toString());
    Assertions.assertEquals(
        "239f59f6ada2835bf34cd04eea2e81f0bacd924c5483233f25365c05e67ecd53", tx0Hash);
    Assertions.assertEquals(
        "01000000000101ae24e3f5dbcee7971ae0e5b83fcb1eb67057901f2d371ca494f868b3dc8c58cc0100000000ffffffff030000000000000000426a409ae6649a7b1fc8a917f408cbf7b41e27f3a5484650aafdf5167852bd348afa8aa8213dda856188683ab187a902923e7ec3b672a6fbb637a4063c71879f6859171027000000000000160014f6a884f18f4d7e78a4167c3e56773c3ae58e0164d6420f00000000001600141dffe6e395c95927e4a16e8e6bd6d05604447e4d02483045022100815871dad73b7fdb6c8cf5aeec754e23e784170d45d1f1f3206a6a43773a9e87022031105b0e4faa319d46d2c7b9d546f128b19f1d027be11ea60f166d8856f43cef01210349baf197181fe53937d225d0e7bd14d8b5f921813c038a95d7c2648500c119b000000000",
        tx0Hex);
  }

  @Test
  public void tx0_1premix_withChange() throws Exception {
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
            Coin.valueOf(1021397)); // balance with 11000 change
    Bip84Wallet depositWallet =
        new Bip84Wallet(bip84w, 0, new MemoryIndexHandler(), new MemoryIndexHandler());
    Bip84Wallet premixWallet =
        new Bip84Wallet(
            bip84w, Integer.MAX_VALUE - 2, new MemoryIndexHandler(), new MemoryIndexHandler());
    int nbOutputsPreferred = 5;
    int nbOutputsExpected = 1;
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
            nbOutputsPreferred,
            premixValue,
            feePaymentCode,
            feePayload);

    Transaction tx = tx0.getTx();
    Assertions.assertEquals(
        nbOutputsExpected + 3, tx.getOutputs().size()); // opReturn + fee (no change)

    String tx0Hash = tx.getHashAsString();
    String tx0Hex = new String(Hex.encode(tx.bitcoinSerialize()));
    log.info(tx0.getTx().toString());
    Assertions.assertEquals(
        "856c10f63b01c7641bd590baa5da4159914c67ac6fef5188a95920ae452f1192", tx0Hash);
    Assertions.assertEquals(
        "01000000000101ae24e3f5dbcee7971ae0e5b83fcb1eb67057901f2d371ca494f868b3dc8c58cc0100000000ffffffff040000000000000000426a409ae6649a7b1fc8a917f408cbf7b41e27f3a5484650aafdf5167852bd348afa8aa8213dda856188683ab187a902923e7ec3b672a6fbb637a4063c71879f6859171027000000000000160014f6a884f18f4d7e78a4167c3e56773c3ae58e0164932a0000000000001600141bd05eb7c9cb516fddd8187cecb2e0cb4e21ac87d6420f00000000001600141dffe6e395c95927e4a16e8e6bd6d05604447e4d024830450221009cfc375edb6df8301b9864c77dae1e7883f44941628107f6cb4d125780b3e0d3022075b3a3802ee9df75eb54c6d2513fc8c2ad26ba6305aec650f2d6dc8daef85bb901210349baf197181fe53937d225d0e7bd14d8b5f921813c038a95d7c2648500c119b000000000",
        tx0Hex);
  }
}
