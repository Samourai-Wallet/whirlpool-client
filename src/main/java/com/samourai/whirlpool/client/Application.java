package com.samourai.whirlpool.client;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.handler.IMixHandler;
import com.samourai.whirlpool.client.mix.handler.MixHandler;
import com.samourai.whirlpool.client.utils.LogbackUtils;
import com.samourai.whirlpool.protocol.v1.notifications.MixStatus;
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

@EnableAutoConfiguration
public class Application implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private static final String ARG_DEBUG = "debug";
    private static final String ARG_NETWORK_ID = "network";
    private static final String ARG_UTXO = "utxo";
    private static final String ARG_UTXO_KEY = "utxo-key";
    private static final String ARG_SEED_PASSPHRASE = "seed-passphrase";
    private static final String ARG_SEED_WORDS = "seed-words";
    private static final String ARG_SERVER = "server";
    private static final String ARG_LIQUIDITY = "liquidity";
    private static final String ARG_MIXS = "mixs";
    private static final String USAGE = "--network={main,test} --utxo= --utxo-key= --seed-passphrase= --seed-words= [--liquidity] [--mixs=1] [--server=host:port] [--debug]";

    private ApplicationArguments args;
    private boolean done;

    public static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        this.args = args;
        this.done = false;

        if (args.containsOption(ARG_DEBUG)) {
            // enable debug logs
            LogbackUtils.setLogLevel("com.samourai.whirlpool.client", Level.DEBUG.toString());
        }

        log.info("------------ whirlpool-client ------------");
        log.info("Running whirlpool-client {}", Arrays.toString(args.getSourceArgs()));

        try {
            String networkId = requireOption(ARG_NETWORK_ID);
            String utxo = requireOption(ARG_UTXO);
            String utxoKey = requireOption(ARG_UTXO_KEY);
            String seedWords = requireOption(ARG_SEED_WORDS);
            String seedPassphrase = requireOption(ARG_SEED_PASSPHRASE);
            boolean liquidity = args.containsOption(ARG_LIQUIDITY);
            final int mixs;
            try {
                mixs = Integer.parseInt(requireOption(ARG_MIXS, "1"));
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Numeric value expected for option: "+ ARG_MIXS);
            }
            String wsUrl = "ws://"+requireOption(ARG_SERVER, "127.0.0.1:8080");

            try {
                runClient(wsUrl, networkId, utxo, utxoKey, seedWords, seedPassphrase, liquidity, mixs);
                synchronized (this) {
                    while(!done) {
                        wait(1000);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        catch(IllegalArgumentException e) {
            log.info("Invalid arguments: "+e.getMessage());
            log.info("Usage: whirlpool-client "+USAGE);
        }
    }

    private WhirlpoolClient runClient(String wsUrl, String networkId, String utxo, String utxoKey, String seedWords, String seedPassphrase, boolean liquidity, int mixs) throws Exception {
        Assert.notNull(wsUrl, "wsUrl is null");
        NetworkParameters params = NetworkParameters.fromPmtProtocolID(networkId);
        Assert.notNull(params, "unknown network");
        Assert.notNull(utxo, "utxo is null");
        Assert.notNull(utxoKey, "utxoKey is null");
        Assert.notNull(seedWords, "seedWords are null");
        Assert.notNull(seedPassphrase, "seedPassphrase is null");
        Assert.isTrue(mixs > 0, "mixs should be > 0");

        // initialize bitcoinj context
        new Context(params);

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
        WhirlpoolClientConfig config = new WhirlpoolClientConfig(wsUrl, params);
        WhirlpoolClient whirlpoolClient = new WhirlpoolClient(config);

        String utxoSplit[] = utxo.split("-");
        String utxoHash = utxoSplit[0];
        Long utxoIdx = Long.parseLong(utxoSplit[1]);
        IMixHandler mixHandler = new MixHandler(ecKey, bip47w);
        MixParams mixParams = new MixParams(utxoHash, utxoIdx, paymentCode, mixHandler, liquidity);
        WhirlpoolClientListener listener = computeClientListener();

        whirlpoolClient.whirlpool(mixParams, mixs, listener);
        return whirlpoolClient;
    }

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

    private String requireOption(String name) {
        if (!args.getOptionNames().contains(name)) {
            throw new IllegalArgumentException("Missing required option: "+name);
        }
        return args.getOptionValues(name).iterator().next();
    }

    private String requireOption(String name, String defaultValue) {
        if (!args.getOptionNames().contains(name)) {
            return defaultValue;
        }
        return args.getOptionValues(name).iterator().next();
    }
}
