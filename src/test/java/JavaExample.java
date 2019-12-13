import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.BackendServer;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Config;
import com.samourai.whirlpool.client.tx0.UnspentOutputWithKey;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.beans.*;
import com.samourai.whirlpool.client.wallet.persist.FileWhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import io.reactivex.functions.Consumer;
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
    BackendApi backendApi = new BackendApi(httpClient, backendUrl, null);

    NetworkParameters params = whirlpoolServer.getParams();
    WhirlpoolWalletConfig whirlpoolWalletConfig =
        new WhirlpoolWalletConfig(
            httpClient, stompClientService, persistHandler, serverUrl, params, backendApi);

    whirlpoolWalletConfig.setAutoTx0PoolId(null); // disable auto-tx0
    whirlpoolWalletConfig.setAutoMix(false); // disable auto-mix

    // configure optional settings (or don't set anything for using default values)
    whirlpoolWalletConfig.setScode("foo");
    whirlpoolWalletConfig.setMaxClients(1);
    whirlpoolWalletConfig.setClientDelay(15);

    return whirlpoolWalletConfig;
  }

  public void example() throws Exception {
    /*
     * CONFIGURATION
     */
    // configure whirlpool
    WhirlpoolWalletService whirlpoolWalletService = new WhirlpoolWalletService();
    WhirlpoolWalletConfig config = computeWhirlpoolWalletConfig();

    /*
     * WALLET
     */
    // open wallet
    HD_Wallet bip84w = null; // provide your wallet here
    WhirlpoolWallet whirlpoolWallet = whirlpoolWalletService.openWallet(config, bip84w);

    // start whirlpool wallet
    whirlpoolWallet.start();

    // get mixing state (started, utxosMixing, nbMixing, nbQueued...)
    MixingState mixingState = whirlpoolWallet.getMixingState();

    // observe mixing state
    mixingState
        .getObservable()
        .subscribe(
            new Consumer<MixingState>() {
              @Override
              public void accept(MixingState mixingState) throws Exception {
                // get mixing utxos
                Collection<WhirlpoolUtxo> mixingUtxos = mixingState.getUtxosMixing();
                if (mixingUtxos.isEmpty()) {
                  // no utxo mixing currently
                  return;
                }

                // one utxo (at least) is mixing currently
                WhirlpoolUtxo mixingUtxo = mixingUtxos.iterator().next();

                // get mixing progress for this utxo
                MixProgress mixProgress = mixingUtxo.getUtxoState().getMixProgress();
                MixStep mixStep = mixProgress.getMixStep(); // CONNECTING, CONNECTED...
                int progressPercent = mixProgress.getProgressPercent();
              }
            });

    /*
     * POOLS
     */
    // list pools
    Collection<Pool> pools = whirlpoolWallet.getPools();

    // find pool by poolId
    Pool pool05btc = whirlpoolWallet.findPoolById("0.5btc");

    /*
     * UTXOS
     */
    // list utxos
    Collection<WhirlpoolUtxo> utxosDeposit = whirlpoolWallet.getUtxosDeposit();
    Collection<WhirlpoolUtxo> utxosPremix = whirlpoolWallet.getUtxosPremix();
    Collection<WhirlpoolUtxo> utxosPostmix = whirlpoolWallet.getUtxosPostmix();

    // get specific utxo
    WhirlpoolUtxo whirlpoolUtxo =
        whirlpoolWallet.findUtxo(
            "040df121854c7db49e38b6fcb61c2b0953c8b234ce53c1b2a2fb122a4e1c3d2e", 1);

    // get specific utxoConfig (poolId, mixsTarget, mixsDone, lastModified...)
    WhirlpoolUtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfig();

    // set specific utxoConfig
    utxoConfig.setMixsTarget(5);
    utxoConfig.setPoolId("0.01btc");

    // observe specific utxo config
    utxoConfig.getObservable().subscribe(/* ... */ );

    // get specific utxo state (status, mixStep, mixableStatus, progressPercent, message, error...)
    WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();

    // observe specific utxo state
    utxoState.getObservable().subscribe(/* ... */ );

    /*
     * TX0
     */
    // tx0 method 1: spending a whirlpool-managed utxo
    {
      // whirlpool utxo for tx0
      String utxoHash = "6517ece36402a89d76d075c60a8d3d0e051e4e5efa42a01c9033328707631b61";
      int utxoIndex = 2;
      whirlpoolUtxo = whirlpoolWallet.findUtxo(utxoHash, utxoIndex);
      if (whirlpoolUtxo == null) {} // utxo not found

      // configure tx0
      Tx0Config tx0Config = whirlpoolWallet.getTx0Config().setBadbankChange(false);
      Tx0FeeTarget minerFeeTarget = Tx0FeeTarget.BLOCKS_4;

      // find eligible pools for this utxo
      Collection<Pool> eligiblePools =
          whirlpoolWallet.findPoolsForTx0(whirlpoolUtxo.getUtxo().value, 1, minerFeeTarget);

      // choose pool
      whirlpoolWallet.setPool(whirlpoolUtxo, "0.01btc");

      // execute tx0
      try {
        Tx0 tx0 = whirlpoolWallet.tx0(whirlpoolUtxo, tx0Config, minerFeeTarget);
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

      // pool for tx0
      Pool pool = whirlpoolWallet.findPoolById("0.01btc"); // provide poolId
      Tx0FeeTarget feeTarget = Tx0FeeTarget.BLOCKS_4;

      // execute tx0
      try {
        Tx0 tx0 =
            whirlpoolWallet.tx0(
                Lists.of(new UnspentOutputWithKey(spendFrom, spendFromPrivKey)),
                pool,
                new Tx0Config(),
                feeTarget);
        String txid = tx0.getTx().getHashAsString(); // get txid
      } catch (Exception e) {
        // tx0 failed
      }
    }

    /*
     * MIX
     */
    whirlpoolWallet.mix(whirlpoolUtxo).subscribe(/* ... */ );

    // stop mixing specific utxo (or remove it from mix queue)
    whirlpoolWallet.mixStop(whirlpoolUtxo);

    // stop Whirlpool
    whirlpoolWalletService.closeWallet();
  }
}
