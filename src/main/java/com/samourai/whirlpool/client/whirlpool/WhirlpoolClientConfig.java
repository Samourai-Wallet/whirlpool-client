package com.samourai.whirlpool.client.whirlpool;

import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClient;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import org.bitcoinj.core.NetworkParameters;

public class WhirlpoolClientConfig {
  private IHttpClient httpClient;
  private IStompClient stompClient;
  private WhirlpoolWalletPersistHandler persistHandler;
  private String server;
  private NetworkParameters networkParameters;
  private boolean ssl;
  private int reconnectDelay;
  private int reconnectUntil;
  private boolean testMode;
  private String scode;

  public WhirlpoolClientConfig(
      IHttpClient httpClient,
      IStompClient stompClient,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters networkParameters,
      boolean ssl) {
    this(
        httpClient,
        stompClient,
        persistHandler,
        server,
        networkParameters,
        ssl,
        null,
        5,
        500,
        false);
  }

  public WhirlpoolClientConfig(
      IHttpClient httpClient,
      IStompClient stompClient,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters networkParameters,
      boolean ssl,
      String scode,
      int reconnectDelay,
      int reconnectUntil,
      boolean testMode) {
    this.httpClient = httpClient;
    this.stompClient = stompClient;
    this.persistHandler = persistHandler;
    this.server = server;
    this.networkParameters = networkParameters;
    this.ssl = ssl;
    this.reconnectDelay = reconnectDelay;
    this.reconnectUntil = reconnectUntil;
    this.testMode = testMode;
    this.scode = scode;
  }

  private WhirlpoolClientConfig(WhirlpoolClientConfig copy) {
    this.httpClient = copy.httpClient;
    this.stompClient = copy.stompClient.copyForNewClient();
    this.persistHandler = copy.persistHandler;
    this.server = copy.server;
    this.networkParameters = copy.networkParameters;
    this.ssl = copy.ssl;
    this.reconnectDelay = copy.reconnectDelay;
    this.reconnectUntil = copy.reconnectUntil;
    this.testMode = copy.testMode;
    this.scode = copy.scode;
  }

  private WhirlpoolClientConfig copyForNewClient() {
    return new WhirlpoolClientConfig(this);
  }

  public WhirlpoolClient newClient() {
    WhirlpoolClientConfig whirlpoolClientConfig = copyForNewClient();
    return WhirlpoolClientImpl.newClient(whirlpoolClientConfig);
  }

  public IHttpClient getHttpClient() {
    return httpClient;
  }

  public IStompClient getStompClient() {
    return stompClient;
  }

  public WhirlpoolWalletPersistHandler getPersistHandler() {
    return persistHandler;
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

  public String getScode() {
    return scode;
  }

  public void setScode(String scode) {
    this.scode = scode;
  }
}
