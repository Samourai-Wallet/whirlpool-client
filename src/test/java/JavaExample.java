import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClient;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Arrays;
import java.util.Collection;
import org.bitcoinj.core.TransactionOutPoint;

public class JavaExample {

  // TODO configure these values as you wish
  private WhirlpoolWalletConfig computeWhirlpoolWalletConfig() {
    IHttpClient httpClient = null; // provide impl here, ie: new AndroidHttpclient();
    IStompClient stompClient = null; // provide impl here, ie: new AndroidStompClient();
    WhirlpoolServer whirlpoolServer = WhirlpoolServer.TEST;

    WhirlpoolWalletConfig whirlpoolWalletConfig =
        new WhirlpoolWalletConfig(httpClient, stompClient, whirlpoolServer);

    // configure optional settings (or don't set anything for using default values)
    whirlpoolWalletConfig.setScode("foo");
    whirlpoolWalletConfig.setMaxClients(1);
    whirlpoolWalletConfig.setClientDelay(15);
    whirlpoolWalletConfig.setAutoTx0(false);
    whirlpoolWalletConfig.setAutoMix(false);
    whirlpoolWalletConfig.setPoolIdsByPriority(Arrays.asList("0.01btc", "0.05btc"));

    return whirlpoolWalletConfig;
  }

  public void example() throws Exception {
    // configure whirlpool
    WhirlpoolWalletConfig whirlpoolWalletConfig = computeWhirlpoolWalletConfig();
    WhirlpoolWalletService whirlpoolWalletService =
        new WhirlpoolWalletService(whirlpoolWalletConfig);

    // configure wallet
    HD_Wallet bip84w = null; // provide your wallet here

    // configure wallet indexs
    IIndexHandler depositIndexHandler = new MemoryIndexHandler();
    IIndexHandler depositChangeIndexHandler = new MemoryIndexHandler();
    IIndexHandler premixIndexHandler = new MemoryIndexHandler();
    IIndexHandler premixChangeIndexHandler = new MemoryIndexHandler();
    IIndexHandler postmixIndexHandler = new MemoryIndexHandler();
    IIndexHandler postmixChangeIndexHandler = new MemoryIndexHandler();
    IIndexHandler feeIndexHandler = new MemoryIndexHandler();

    WhirlpoolWallet whirlpoolWallet =
        whirlpoolWalletService.openWallet(
            bip84w,
            depositIndexHandler,
            depositChangeIndexHandler,
            premixIndexHandler,
            premixChangeIndexHandler,
            postmixIndexHandler,
            postmixChangeIndexHandler,
            feeIndexHandler);

    // start wallet
    whirlpoolWallet.start();

    // get state
    WhirlpoolWalletState whirlpoolWalletState = whirlpoolWallet.getState();

    // tx0 spending a whirlpool-managed utxo
    {
      // whirlpool utxo for tx0
      String utxoHash = "6517ece36402a89d76d075c60a8d3d0e051e4e5efa42a01c9033328707631b61";
      int utxoIndex = 2;
      WhirlpoolUtxo whirlpoolUtxo = whirlpoolWallet.findUtxo(utxoHash, utxoIndex);
      if (whirlpoolUtxo == null) {} // utxo not found

      // find eligible pools for this utxo
      int nbOutputsMinForTx0 = 1;
      Collection<Pool> eligiblePools =
          whirlpoolWallet.findTx0Pools(whirlpoolUtxo.getUtxo().value, nbOutputsMinForTx0);

      // pool for tx0
      Pool pool = eligiblePools.iterator().next();

      // execute tx0
      try {
        Tx0 tx0 = whirlpoolWallet.tx0(whirlpoolUtxo, pool);
        String txid = tx0.getTx().getHashAsString(); // get txid
      } catch (Exception e) {
        // tx0 failed
      }
    }

    // tx0 spending an external utxo
    {
      // external utxo for tx0
      TransactionOutPoint spendFromOutpoint = null; // provide utxo outpoint
      byte[] spendFromPrivKey = null; // provide utxo private key
      long spendFromValue = 12345678; // provide utxo value

      // pool for tx0
      Pool pool = whirlpoolWallet.getPools().findPoolById("0.01btc"); // provide poolId

      // execute tx0
      try {
        Tx0 tx0 = whirlpoolWallet.tx0(spendFromOutpoint, spendFromPrivKey, spendFromValue, pool);
        String txid = tx0.getTx().getHashAsString(); // get txid
      } catch (Exception e) {
        // tx0 failed
      }
    }
  }
}
