import com.samourai.api.client.SamouraiApi;
import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolServer;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolWalletState;
import com.samourai.whirlpool.client.wallet.persist.FileWhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import java.io.File;
import java.util.Collection;
import java8.util.Lists;
import org.bitcoinj.core.NetworkParameters;

public class JavaExample {

  // TODO configure these values as you wish
  private WhirlpoolWalletConfig computeWhirlpoolWalletConfig() {
    IHttpClient httpClient = null; // provide impl here, ie: new AndroidHttpclient();
    IStompClientService stompClientService =
        null; // provide impl here, ie: new AndroidStompClientService();
    WhirlpoolWalletPersistHandler persistHandler =
        new FileWhirlpoolWalletPersistHandler(new File("/tmp/state"), new File("/tmp/utxos"));

    WhirlpoolServer whirlpoolServer = WhirlpoolServer.TESTNET;

    boolean onion = true;
    String serverUrl = whirlpoolServer.getServerUrl(onion);
    String backendUrl = BackendServer.TESTNET.getBackendUrl(onion);
    SamouraiApi samouraiApi = new SamouraiApi(httpClient, backendUrl, null);

    NetworkParameters params = whirlpoolServer.getParams();
    WhirlpoolWalletConfig whirlpoolWalletConfig =
        new WhirlpoolWalletConfig(
            httpClient, stompClientService, persistHandler, serverUrl, params, samouraiApi);

    whirlpoolWalletConfig.setAutoTx0PoolId(null); // disable auto-tx0
    whirlpoolWalletConfig.setAutoMix(false); // disable auto-mix

    // configure optional settings (or don't set anything for using default values)
    whirlpoolWalletConfig.setScode("foo");
    whirlpoolWalletConfig.setMaxClients(1);
    whirlpoolWalletConfig.setClientDelay(15);

    return whirlpoolWalletConfig;
  }

  public void example() throws Exception {
    // configure whirlpool
    WhirlpoolWalletConfig config = computeWhirlpoolWalletConfig();

    // configure wallet
    HD_Wallet bip84w = null; // provide your wallet here

    WhirlpoolWallet whirlpoolWallet = new WhirlpoolWalletService().openWallet(config, bip84w);

    // start whirlpool wallet
    whirlpoolWallet.start();

    // get state
    WhirlpoolWalletState whirlpoolWalletState = whirlpoolWallet.getState();

    // list pools
    Collection<Pool> pools = whirlpoolWallet.getPools();

    // find pool by poolId
    Pool pool05btc = whirlpoolWallet.findPoolById("0.5btc");

    // tx0 method 1: spending a whirlpool-managed utxo
    {
      // whirlpool utxo for tx0
      String utxoHash = "6517ece36402a89d76d075c60a8d3d0e051e4e5efa42a01c9033328707631b61";
      int utxoIndex = 2;
      WhirlpoolUtxo whirlpoolUtxo = whirlpoolWallet.findUtxo(utxoHash, utxoIndex);
      if (whirlpoolUtxo == null) {} // utxo not found

      // find eligible pools for this utxo
      Tx0FeeTarget feeTarget = Tx0FeeTarget.BLOCKS_4;
      Collection<Pool> eligiblePools =
          whirlpoolWallet.findPoolsForTx0(whirlpoolUtxo.getUtxo().value, 1, feeTarget);

      // choose pool
      whirlpoolWallet.setPool(whirlpoolUtxo, "0.01btc");

      // execute tx0
      try {
        Tx0 tx0 = whirlpoolWallet.tx0(whirlpoolUtxo, feeTarget);
        String txid = tx0.getTx().getHashAsString(); // get txid
      } catch (Exception e) {
        // tx0 failed
      }
    }

    // tx0 method 2: spending an external utxo
    {
      // external utxo for tx0
      UnspentResponse.UnspentOutput spendFrom = null; // provide utxo outpoint
      byte[] spendFromPrivKey = null; // provide utxo private key
      long spendFromValue = 12345678; // provide utxo value

      // pool for tx0
      Pool pool = whirlpoolWallet.findPoolById("0.01btc"); // provide poolId
      Tx0FeeTarget feeTarget = Tx0FeeTarget.BLOCKS_4;

      // execute tx0
      try {
        Tx0 tx0 =
            whirlpoolWallet.tx0(
                Lists.of(spendFrom), spendFromPrivKey, spendFromValue, pool, feeTarget);
        String txid = tx0.getTx().getHashAsString(); // get txid
      } catch (Exception e) {
        // tx0 failed
      }
    }

    // list premix utxos
    Collection<WhirlpoolUtxo> utxosPremix = whirlpoolWallet.getUtxosPremix();

    // mix specific utxo
    WhirlpoolUtxo whirlpoolUtxo = utxosPremix.iterator().next();
    WhirlpoolClientListener listener =
        new WhirlpoolClientListener() {
          @Override
          public void success(MixSuccess mixSuccess) {
            // mix success
          }

          @Override
          public void fail(MixFailReason reason, String notifiableError) {
            // mix failed
          }

          @Override
          public void progress(MixStep step) {
            // mix progress
          }
        };
    whirlpoolWallet.mix(whirlpoolUtxo, listener);

    // stop mixing specific utxo
    whirlpoolWallet.mixStop(whirlpoolUtxo);

    // get global mix state
    WhirlpoolWalletState whirlpoolState = whirlpoolWallet.getState();
  }
}
