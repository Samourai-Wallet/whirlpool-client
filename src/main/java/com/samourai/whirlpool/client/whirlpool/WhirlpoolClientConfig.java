package com.samourai.whirlpool.client.whirlpool;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.http.client.IHttpClientService;
import com.samourai.stomp.client.IStompClientService;
import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.wallet.persist.WhirlpoolWalletPersistHandler;
import org.bitcoinj.core.NetworkParameters;

public class WhirlpoolClientConfig {
  private IHttpClientService httpClientService;
  private IStompClientService stompClientService;
  private WhirlpoolWalletPersistHandler persistHandler;
  private String server;
  private NetworkParameters networkParameters;
  private boolean mobile;
  private int reconnectDelay;
  private int reconnectUntil;
  private String scode;

  public WhirlpoolClientConfig(
      IHttpClientService httpClientService,
      IStompClientService stompClientService,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters networkParameters,
      boolean mobile) {
    this(
        httpClientService,
        stompClientService,
        persistHandler,
        server,
        networkParameters,
        mobile,
        null,
        5,
        500);
  }

  public WhirlpoolClientConfig(
      IHttpClientService httpClientService,
      IStompClientService stompClientService,
      WhirlpoolWalletPersistHandler persistHandler,
      String server,
      NetworkParameters networkParameters,
      boolean mobile,
      String scode,
      int reconnectDelay,
      int reconnectUntil) {
    this.httpClientService = httpClientService;
    this.stompClientService = stompClientService;
    this.persistHandler = persistHandler;
    this.server = server;
    this.networkParameters = networkParameters;
    this.mobile = mobile;
    this.reconnectDelay = reconnectDelay;
    this.reconnectUntil = reconnectUntil;
    this.scode = scode;
  }

  public WhirlpoolClient newClient() {
    return WhirlpoolClientImpl.newClient(this);
  }

  public IHttpClient getHttpClient(HttpUsage httpUsage) {
    return httpClientService.getHttpClient(httpUsage);
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

  public boolean isMobile() {
    return mobile;
  }

  public void setMobile(boolean mobile) {
    this.mobile = mobile;
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

  public String getScode() {
    return scode;
  }

  public void setScode(String scode) {
    this.scode = scode;
  }
}
