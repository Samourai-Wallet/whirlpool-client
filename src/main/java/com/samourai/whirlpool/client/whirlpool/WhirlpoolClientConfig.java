package com.samourai.whirlpool.client.whirlpool;

import com.samourai.http.client.IHttpClient;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import org.bitcoinj.core.NetworkParameters;

public class WhirlpoolClientConfig {
  private IHttpClient httpClient;
  private IStompClientService stompClientService;
  private WhirlpoolWalletPersistHandler persistHandler;
  private String server;
  private NetworkParameters networkParameters;
  private int reconnectDelay;
  private int reconnectUntil;
  private boolean testMode;
  private String scode;

  public WhirlpoolClientConfig(
      IHttpClient httpClient,
      IStompClientService stompClientService,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters networkParameters) {
    this(
        httpClient,
        stompClientService,
        persistHandler,
        server,
        networkParameters,
        null,
        5,
        500,
        false);
  }

  public WhirlpoolClientConfig(
      IHttpClient httpClient,
      IStompClientService stompClientService,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters networkParameters,
      String scode,
      int reconnectDelay,
      int reconnectUntil,
      boolean testMode) {
    this.httpClient = httpClient;
    this.stompClientService = stompClientService;
    this.persistHandler = persistHandler;
    this.server = server;
    this.networkParameters = networkParameters;
    this.reconnectDelay = reconnectDelay;
    this.reconnectUntil = reconnectUntil;
    this.testMode = testMode;
    this.scode = scode;
  }

  public WhirlpoolClient newClient() {
    return WhirlpoolClientImpl.newClient(this);
  }

  public IHttpClient getHttpClient() {
    return httpClient;
  }

  public IStompClientService getStompClientService() {
    return stompClientService;
  }

  public WhirlpoolWalletPersistHandler getPersistHandler() {
    return persistHandler;
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

  public String getScode() {
    return scode;
  }

  public void setScode(String scode) {
    this.scode = scode;
  }
}
