package com.samourai.whirlpool.client.beans;

import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import org.bitcoinj.core.ECKey;

import java.util.Map;

public class PremixResult {
    private Map<String,String> mixables;
    private Map<String, ECKey> toPrivKeys;
    private Map<String, String> toUTXO;
    private String paymentCode;
    private BIP47Wallet bip47wallet;
    
    public PremixResult(Map<String,String> mixables, Map<String, ECKey> toPrivKeys, Map<String, String> toUTXO, String paymentCode, BIP47Wallet bip47wallet) {
        this.mixables = mixables;
        this.toPrivKeys = toPrivKeys;
        this.toUTXO = toUTXO;
        this.paymentCode = paymentCode;
        this.bip47wallet = bip47wallet;
    }

    public Map<String, String> getMixables() {
        return mixables;
    }

    public Map<String, ECKey> getToPrivKeys() {
        return toPrivKeys;
    }

    public Map<String, String> getToUTXO() {
        return toUTXO;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public BIP47Wallet getBip47wallet() {
        return bip47wallet;
    }
}
