import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClient;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.client.indexHandler.MemoryIndexHandler;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.exception.EmptyWalletException;
import com.samourai.whirlpool.client.exception.UnconfirmedUtxoException;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import java.util.Arrays;
import org.bitcoinj.core.TransactionOutPoint;

public class JavaExample {

  // TODO configure these values as you wish
  private WhirlpoolWalletConfig computeWhirlpoolWalletConfig() {
    IHttpClient httpClient = null; // TODO provide impl here, ie: new AndroidHttpclient();
    IStompClient stompClient = null; // TODO provide impl here, ie: new AndroidStompClient();
    WhirlpoolServer whirlpoolServer = WhirlpoolServer.TEST;

    WhirlpoolWalletConfig whirlpoolWalletConfig =
        new WhirlpoolWalletConfig(httpClient, stompClient, whirlpoolServer);

    // optional settings
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
    HD_Wallet bip84w = null; // TODO provide your wallet here

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

    // tx0 by automatic selection of best available utxo to spend from deposit
    try {
      Tx0 tx0 = whirlpoolWallet.tx0();
    } catch (EmptyWalletException e) {
      // no deposit utxo found for Tx0
    } catch (UnconfirmedUtxoException e) {
      // deposit utxo found for Tx0, but it is unconfirmed
    }

    // tx0 by manually selecting utxo to spend
    TransactionOutPoint spendFromOutpoint = null; // TODO utxo outpoint
    byte[] spendFromPrivKey = null; // TODO utxo private key
    long spendFromValue = 12345678; // TODO utxo value
    Pool pool = whirlpoolWallet.getPools().findPoolById("0.01btc"); // TODO poolId
    try {
      Tx0 tx0 = whirlpoolWallet.tx0(spendFromOutpoint, spendFromPrivKey, spendFromValue, pool);
      String txid = tx0.getTx().getHashAsString(); // get txid
    } catch (Exception e) {
      // tx0 failed
    }
  }

  // mix
  // TODO
}
