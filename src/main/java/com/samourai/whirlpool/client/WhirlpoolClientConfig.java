package com.samourai.whirlpool.client;

import org.bitcoinj.core.NetworkParameters;

public class WhirlpoolClientConfig {

    private String server;
    private NetworkParameters networkParameters;
    private int reconnectDelay;
    private int reconnectUntil;


    public WhirlpoolClientConfig(String server, NetworkParameters networkParameters) {
        this.server = server;
        this.networkParameters = networkParameters;

        // wait 5 seconds between reconnecting attempt
        this.reconnectDelay = 5;

        // retry reconnecting for 5 minutes
        this.reconnectUntil = 500;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public NetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public void setNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    public int getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(int reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public int getReconnectUntil() {
        return reconnectUntil;
    }

    public void setReconnectUntil(int reconnectUntil) {
        this.reconnectUntil = reconnectUntil;
    }
}
