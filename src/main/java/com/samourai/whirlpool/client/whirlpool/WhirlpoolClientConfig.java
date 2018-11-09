package com.samourai.whirlpool.client.whirlpool;

import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClient;
import org.bitcoinj.core.NetworkParameters;

public class WhirlpoolClientConfig {

  private IHttpClient httpClient;
  private IStompClient stompClient;
  private String server;
  private NetworkParameters networkParameters;
  private boolean ssl;
  private int reconnectDelay;
  private int reconnectUntil;
  private boolean testMode;

  public WhirlpoolClientConfig(
      IHttpClient httpClient,
      IStompClient stompClient,
      String server,
      NetworkParameters networkParameters) {
    this.httpClient = httpClient;
    this.stompClient = stompClient;
    this.server = server;
    this.networkParameters = networkParameters;

    this.ssl = true;

    // wait 5 seconds between reconnecting attempt
    this.reconnectDelay = 5;

    // retry reconnecting for 5 minutes
    this.reconnectUntil = 500;

    this.testMode = false;
  }

  public WhirlpoolClientConfig(WhirlpoolClientConfig copy) {
    this.httpClient = copy.httpClient;
    this.stompClient = copy.stompClient;
    this.server = copy.server;
    this.networkParameters = copy.networkParameters;
    this.ssl = copy.ssl;
    this.reconnectDelay = copy.reconnectDelay;
    this.reconnectUntil = copy.reconnectUntil;
    this.testMode = copy.testMode;
  }

  public IHttpClient getHttpClient() {
    return httpClient;
  }

  public IStompClient getStompClient() {
    return stompClient;
  }

  public void setStompClient(IStompClient stompClient) {
    this.stompClient = stompClient;
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

  public boolean isSsl() {
    return ssl;
  }

  public void setSsl(boolean ssl) {
    this.ssl = ssl;
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
