package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.mix.handler.IMixHandler;

public class MixParams {
    private String utxoHash;
    private long utxoIdx;
    private long utxoBalance;
    private String paymentCode;
    private IMixHandler mixHandler;

    public MixParams(String utxoHash, long utxoIdx, long utxoBalance, String paymentCode, IMixHandler mixHandler) {
        this.utxoHash = utxoHash;
        this.utxoIdx = utxoIdx;
        this.utxoBalance = utxoBalance;
        this.paymentCode = paymentCode;
        this.mixHandler = mixHandler;
    }

    public String getUtxoHash() {
        return utxoHash;
    }

    public long getUtxoIdx() {
        return utxoIdx;
    }

    public long getUtxoBalance() {
        return utxoBalance;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public IMixHandler getMixHandler() {
        return mixHandler;
    }
}
