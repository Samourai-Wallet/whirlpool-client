package com.samourai.whirlpool.client.beans;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public class Pool {
    private String poolId;
    private long denomination;
    private long minerFeeMin;
    private long minerFeeMax;
    private int minAnonymitySet;
    private int mixAnonymitySet;
    private MixStatus mixStatus;
    private long elapsedTime;
    private int mixNbConnected;
    private int mixNbRegistered;

    public Pool() {
    }

    public String getPoolId() {
        return poolId;
    }

    public void setPoolId(String poolId) {
        this.poolId = poolId;
    }

    public long getDenomination() {
        return denomination;
    }

    public void setDenomination(long denomination) {
        this.denomination = denomination;
    }

    public long getMinerFeeMin() {
        return minerFeeMin;
    }

    public void setMinerFeeMin(long minerFeeMin) {
        this.minerFeeMin = minerFeeMin;
    }

    public long getMinerFeeMax() {
        return minerFeeMax;
    }

    public void setMinerFeeMax(long minerFeeMax) {
        this.minerFeeMax = minerFeeMax;
    }

    public int getMinAnonymitySet() {
        return minAnonymitySet;
    }

    public void setMinAnonymitySet(int minAnonymitySet) {
        this.minAnonymitySet = minAnonymitySet;
    }

    public int getMixAnonymitySet() {
        return mixAnonymitySet;
    }

    public void setMixAnonymitySet(int mixAnonymitySet) {
        this.mixAnonymitySet = mixAnonymitySet;
    }

    public MixStatus getMixStatus() {
        return mixStatus;
    }

    public void setMixStatus(MixStatus mixStatus) {
        this.mixStatus = mixStatus;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(long elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public int getMixNbConnected() {
        return mixNbConnected;
    }

    public void setMixNbConnected(int mixNbConnected) {
        this.mixNbConnected = mixNbConnected;
    }

    public int getMixNbRegistered() {
        return mixNbRegistered;
    }

    public void setMixNbRegistered(int mixNbRegistered) {
        this.mixNbRegistered = mixNbRegistered;
    }
}
