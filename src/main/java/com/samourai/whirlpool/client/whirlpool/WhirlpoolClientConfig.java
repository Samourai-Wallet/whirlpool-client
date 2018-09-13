package com.samourai.whirlpool.client.whirlpool;

import com.samourai.whirlpool.client.mix.transport.IStompClient;
import com.samourai.whirlpool.client.whirlpool.httpClient.IWhirlpoolHttpClient;
import org.bitcoinj.core.NetworkParameters;

public class WhirlpoolClientConfig {

    private IWhirlpoolHttpClient httpClient;
    private IStompClient stompClient;
    private String server;
    private NetworkParameters networkParameters;
    private int reconnectDelay;
    private int reconnectUntil;
    private boolean testMode;


    public WhirlpoolClientConfig(IWhirlpoolHttpClient httpClient, IStompClient stompClient, String server, NetworkParameters networkParameters) {
        this.httpClient = httpClient;
        this.stompClient = stompClient;
        this.server = server;
        this.networkParameters = networkParameters;

        // wait 5 seconds between reconnecting attempt
        this.reconnectDelay = 5;

        // retry reconnecting for 5 minutes
        this.reconnectUntil = 500;

        this.testMode = false;
    }

    public IWhirlpoolHttpClient getHttpClient() {
        return httpClient;
    }

    public IStompClient getStompClient() {
        return stompClient;
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

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public boolean isTestMode() {
        return testMode;
    }
}
