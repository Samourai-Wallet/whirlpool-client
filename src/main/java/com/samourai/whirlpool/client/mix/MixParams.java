package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.mix.handler.IMixHandler;

public class MixParams {
    private String utxoHash;
    private long utxoIdx;
    private String paymentCode;
    private IMixHandler mixHandler;
    private boolean liquidity;

    public MixParams(String utxoHash, long utxoIdx, String paymentCode, IMixHandler mixHandler, boolean liquidity) {
        this.utxoHash = utxoHash;
        this.utxoIdx = utxoIdx;
        this.paymentCode = paymentCode;
        this.mixHandler = mixHandler;
        this.liquidity = liquidity;
    }

    public String getUtxoHash() {
        return utxoHash;
    }

    public long getUtxoIdx() {
        return utxoIdx;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public IMixHandler getMixHandler() {
        return mixHandler;
    }

    public boolean isLiquidity() {
        return liquidity;
    }
}
