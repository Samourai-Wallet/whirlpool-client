package com.samourai.whirlpool.client;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.beans.PremixResult;
import com.samourai.whirlpool.client.services.PremixService;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.simple.SimpleWhirlpoolClient;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.MnemonicCode;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class Application {

    private PremixService premixer = new PremixService();

    public static void main(String[] args) throws Exception {
        new Application().runMain();
    }

    private void runMain() {
        System.out.println("whirlpool-client...");

        String wsUrl = "ws://127.0.0.1:8080";
        NetworkParameters params = NetworkParameters.fromPmtProtocolID("test");
        String utxoTx0SpendFrom = "fc359aff8805bf1ecef3ac09630e6689517c01a68946823668e755e1f6e2092d-0";

        new Thread(() -> {
            try {
                runClient(wsUrl, params, utxoTx0SpendFrom);
                synchronized (this) {
                    wait();
                }
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void runClient(String wsUrl, NetworkParameters params, String utxoTx0SpendFrom) throws Exception {
        Assert.notNull(wsUrl, "wsUrl is null");
        Assert.notNull(params, "networkParameters are null");

        // initialize bitcoinj context
        new Context(params);

        // premix
        int nbSpendTos = 5;
        PremixResult premixResult = premix(params, utxoTx0SpendFrom, nbSpendTos);

        // whirlpool
        WhirlpoolClient whirlpoolClient = new WhirlpoolClient(wsUrl, params);
        boolean liquidity = false;
        String firstUtxo = premixResult.getToUTXO().values().iterator().next();
        String firstUtxoSplit[] = firstUtxo.split("-");
        String utxoHash = firstUtxoSplit[0];
        Long utxoIdx = Long.parseLong(firstUtxoSplit[1]);
        ECKey utxoKey = premixResult.getToPrivKeys().values().iterator().next();
        BIP47Wallet bip47Wallet = premixResult.getBip47wallet();
        ISimpleWhirlpoolClient simpleClient = new SimpleWhirlpoolClient(utxoKey, bip47Wallet);
        whirlpoolClient.whirlpool(utxoHash, utxoIdx, premixResult.getPaymentCode(), simpleClient, liquidity);

    }

    private PremixResult premix(NetworkParameters params, String utxoSpendFrom, int nbSpendTos) throws Exception {
        // Samourai fee %
        double swFeePct = 0.0175;
        // mix (round) amount
        double mixAmount = 0.5;
        // Samourai fee
        long swFee = ((long)((mixAmount * swFeePct) * 1e8) + 100000L);  // add 0.001 BTC flat fee per mix
        // example utxo amount
        long selectedAmount = 75000000L;
        // mixable amount to be received
        long unitReceiveAmount = 10000000L;
        // net miner's fee for premix
        long premixFee = 900L;
        // net miner's fee for mix
        long mixFee = 1000L;
        // mixable amount plus own fee
        long unitSpendAmount = unitReceiveAmount + mixFee; //10001000L;

        final String BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db";
        InputStream wis = HD_Wallet.class.getResourceAsStream("/en_US.txt");
        List<String> seedWords = Arrays.asList("all all all all all all all all all all all all".split("\\s+"));
        String passphrase = "all" + Integer.toString(10 + 0);
        MnemonicCode mc = new MnemonicCode(wis, BIP39_ENGLISH_SHA256);

        PremixResult premixResult = premixer.premix(params, seedWords, passphrase, mc, utxoSpendFrom, nbSpendTos, swFee, selectedAmount, unitSpendAmount, premixFee);

        wis.close();
        return premixResult;
    }

}
