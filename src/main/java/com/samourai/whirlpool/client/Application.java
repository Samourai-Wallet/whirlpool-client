package com.samourai.whirlpool.client;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.client.beans.Pools;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.IMixHandler;
import com.samourai.whirlpool.client.mix.handler.MixHandler;
import com.samourai.whirlpool.client.utils.LogbackUtils;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicCode;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line client.
 */
@EnableAutoConfiguration
public class Application implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private ApplicationArgs appArgs;
    private boolean done = false;

    public static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        this.appArgs = new ApplicationArgs(args);

        // enable debug logs with --debug
        if (appArgs.isDebug()) {
            LogbackUtils.setLogLevel("com.samourai.whirlpool.client", Level.DEBUG.toString());
        }

        log.info("------------ whirlpool-client ------------");
        log.info("Running whirlpool-client {}", Arrays.toString(args.getSourceArgs()));
        try {
            String server = appArgs.getServer();
            NetworkParameters params = appArgs.getNetworkParameters();
            new Context(params); // initialize bitcoinj context

            // instanciate client
            WhirlpoolClientConfig config = new WhirlpoolClientConfig(server, params);
            WhirlpoolClient whirlpoolClient = new WhirlpoolClient(config);

            String poolId = appArgs.getPoolId();
            if (poolId == null) {
                // show pools list if --pool is not provided
                listPools(whirlpoolClient);
            } else {
                // go whirlpool if --pool is provided
                whirlpool(whirlpoolClient, poolId, params);
            }
        }
        catch(IllegalArgumentException e) {
            log.info("Invalid arguments: "+e.getMessage());
            log.info("Usage: whirlpool-client "+ApplicationArgs.USAGE);
        }
    }

    // show available pools
    private void listPools(WhirlpoolClient whirlpoolClient) {
        log.info(" • Retrieving pools...");
        try {
            Pools pools = whirlpoolClient.listPools(); // request pools list

            String lineFormat = "| %15s | %6s | %15s | %22s | %12s | %15s | %13s |\n";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(lineFormat, "POOL ID", "DENOM.", "STATUS", "USERS", "ELAPSED TIME", "ANONYMITY SET", "MINER FEE"));
            sb.append(String.format(lineFormat, "", "(btc)", "", "(registered/connected)", "", "(target/min)", "min-max (sat)"));
            pools.getPools().forEach(pool -> {
                sb.append(String.format(lineFormat, pool.getPoolId(),  satToBtc(pool.getDenomination()), pool.getMixStatus(), pool.getMixNbRegistered() + " / " + pool.getMixNbConnected(), pool.getElapsedTime()/1000 + "s", pool.getMixAnonymitySet() + " / " + pool.getMinAnonymitySet(), pool.getMinerFeeMin() + " - " + pool.getMinerFeeMax()));
            });
            log.info("\n" + sb.toString());
            log.info("Tip: use --pool argument to select a pool");
        } catch(Exception e) {
            log.error("", e);
        }
    }

    // start mixing in a pool
    private void whirlpool(WhirlpoolClient whirlpoolClient, String poolId, NetworkParameters params) {
        String utxoHash = appArgs.getUtxoHash();
        long utxoIdx = appArgs.getUtxoIdx();
        String utxoKey = appArgs.getUtxoKey();
        long utxoBalance = appArgs.getUtxoBalance();
        String seedWords = appArgs.getSeedWords();
        String seedPassphrase = appArgs.getSeedPassphrase();
        final int mixs = appArgs.getMixs();

        try {
            runWhirlpool(whirlpoolClient, poolId, params, utxoHash, utxoIdx, utxoKey, utxoBalance, seedWords, seedPassphrase, mixs);
            waitDone();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private WhirlpoolClient runWhirlpool(WhirlpoolClient whirlpoolClient, String poolId, NetworkParameters params, String utxoHash, long utxoIdx, String utxoKey, long utxoBalance, String seedWords, String seedPassphrase, int mixs) throws Exception {
        // utxo key
        DumpedPrivateKey dumpedPrivateKey = new DumpedPrivateKey(params, utxoKey);
        ECKey ecKey = dumpedPrivateKey.getKey();

        // wallet
        final String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";
        InputStream wis = HD_Wallet.class.getResourceAsStream("/en_US.txt");
        List<String> seedWordsList = Arrays.asList(seedWords.split("\\s+"));
        MnemonicCode mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);
        byte[] seed = mc.toEntropy(seedWordsList);

        // init BIP44 wallet
        HD_Wallet hdw = new HD_Wallet(44, mc, params, seed, seedPassphrase, 1);
        // init BIP47 wallet for input
        BIP47Wallet bip47w = new BIP47Wallet(47, mc, params, Hex.decode(hdw.getSeedHex()), hdw.getPassphrase(), 1);
        String paymentCode = bip47w.getAccount(0).getPaymentCode();

        // whirlpool

        IMixHandler mixHandler = new MixHandler(ecKey, bip47w);
        MixParams mixParams = new MixParams(utxoHash, utxoIdx, utxoBalance, paymentCode, mixHandler);
        WhirlpoolClientListener listener = computeClientListener();

        whirlpoolClient.whirlpool(poolId, mixParams, mixs, listener);
        return whirlpoolClient;
    }

    // this listener gets notified of mix status in real time
    private WhirlpoolClientListener computeClientListener() {
        return new WhirlpoolClientListener() {
            @Override
            public void success(int nbMixs) {
                done = true;
                log.info("***** ALL " + nbMixs + " MIXS SUCCESS *****");
            }

            @Override
            public void fail(int currentMix, int nbMixs) {
                done = true;
                log.info("***** MIX " + currentMix + "/" + nbMixs + " FAILED *****");
            }

            @Override
            public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
                log.info("***** MIX " + currentMix + "/" + nbMixs + " SUCCESS *****");
            }

            @Override
            public void progress(int currentMix, int nbMixs, MixStatus mixStatus, int currentStep, int nbSteps) {

            }
        };
    }

    private void waitDone() throws InterruptedException {
        synchronized (this) {
            while(!done) {
                wait(1000);
            }
        }
    }

    private double satToBtc(long sat) {
        return sat / 100000000.0;
    }
}
