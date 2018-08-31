package com.samourai.whirlpool.client.mix.listener;

public class MixSuccess {
    private String receiveAddress;
    private String receiveUtxoHash;
    private int receiveUtxoIdx;

    public MixSuccess(String receiveAddress, String receiveUtxoHash, int receiveUtxoIdx) {
        this.receiveAddress = receiveAddress;
        this.receiveUtxoHash = receiveUtxoHash;
        this.receiveUtxoIdx = receiveUtxoIdx;
    }

    public String getReceiveAddress() {
        return receiveAddress;
    }

    public String getReceiveUtxoHash() {
        return receiveUtxoHash;
    }

    public int getReceiveUtxoIdx() {
        return receiveUtxoIdx;
    }
}