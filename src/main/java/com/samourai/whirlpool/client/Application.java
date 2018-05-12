package com.samourai.whirlpool.client;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import com.samourai.whirlpool.client.utils.LogbackUtils;
import org.apache.log4j.Level;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicCode;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final String ARG_NETWORK_ID = "network";
    private static final String ARG_UTXO = "utxo";
    private static final String ARG_UTXO_KEY = "utxo-key";
    private static final String ARG_SEED_PASSPHRASE = "seed-passphrase";
    private static final String ARG_SEED_WORDS = "seed-words";
    private static final String ARG_SERVER = "server";
    private static final String USAGE = "--network={main,test} --utxo= --utxo-key= --seed-passphrase= --seed-words= [--server=host:port] [--debug]";

    private ApplicationArguments args;

    public static void main(String... args) throws Exception {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.args = args;

        if (args.containsOption("debug")) {
            // enable debug logs
            LogbackUtils.setLogLevel("com.samourai.whirlpool.client", Level.DEBUG.toString());
        }

        logger.info("--------------------------------------");
        logger.info("Running whirlpool-client {}", Arrays.toString(args.getSourceArgs()));

        try {
            String networkId = requireOption(ARG_NETWORK_ID);
            String utxo = requireOption(ARG_UTXO);
            String utxoKey = requireOption(ARG_UTXO_KEY);
            String seedWords = requireOption(ARG_SEED_WORDS);
            String seedPassphrase = requireOption(ARG_SEED_PASSPHRASE);
            String wsUrl = "ws://"+requireOption(ARG_SERVER, "127.0.0.1:8080");

            new Thread(() -> {
                try {
                    WhirlpoolClient whirlpoolClient = runClient(wsUrl, networkId, utxo, utxoKey, seedWords, seedPassphrase);
                    synchronized (this) {
                        while(!whirlpoolClient.isDone()) {
                            wait(1000);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        catch(IllegalArgumentException e) {
            logger.info("Invalid arguments: "+e.getMessage());
            logger.info("Usage: whirlpool-client "+USAGE);
        }
    }

    private WhirlpoolClient runClient(String wsUrl, String networkId, String utxo, String utxoKey, String seedWords, String seedPassphrase) throws Exception {
        Assert.notNull(wsUrl, "wsUrl is null");
        NetworkParameters params = NetworkParameters.fromPmtProtocolID(networkId);
        Assert.notNull(params, "unknown network");
        Assert.notNull(utxo, "utxo are null");
        Assert.notNull(utxoKey, "utxoWif are null");
        Assert.notNull(seedWords, "seedWords are null");
        Assert.notNull(seedPassphrase, "seedPassphrase is null");

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
        WhirlpoolClient whirlpoolClient = new WhirlpoolClient(wsUrl, params);
        boolean liquidity = false;
        String utxoSplit[] = utxo.split("-");
        String utxoHash = utxoSplit[0];
        Long utxoIdx = Long.parseLong(utxoSplit[1]);
        ISimpleWhirlpoolClient simpleClient = new SimpleWhirlpoolClient(ecKey, bip47w);
        whirlpoolClient.whirlpool(utxoHash, utxoIdx, paymentCode, simpleClient, liquidity);
        return whirlpoolClient;
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
