package com.samourai.whirlpool.client.wallet.beans;

import java8.util.Optional;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

public enum WhirlpoolServer {
  TESTNET(
      "pool.whirl.mx:8081",
      "kbwdyvawuqsniaop.onion",
      "y5qvjlxvbohc73slq4j4qldoegyukvpp74mbsrjosnrsgg7w5fon6nyd.onion",
      TestNet3Params.get(),
      true),
  INTEGRATION(
      "pool.whirl.mx:8082",
      "mgsr5lru3csqfpjc.onion",
      "yuvewbfkftftcbzn54lfx3i5s4jxr4sfgpsbkvcflgzcvumyxrkopmyd.onion",
      TestNet3Params.get(),
      true),
  MAINNET(
      "pool.whirl.mx:8080",
      "valnvwglmmavmhfi.onion",
      "udkmfc5j6zvv3ysavbrwzhwji4hpyfe3apqa6yst7c7l32mygf65g4ad.onion",
      MainNetParams.get(),
      true),
  LOCAL_TESTNET("127.0.0.1:8080", null, null, TestNet3Params.get(), false);

  private String serverUrl;
  private String serverOnionV2;
  private String serverOnionV3;
  private NetworkParameters params;
  private boolean ssl;

  WhirlpoolServer(
      String serverUrl,
      String serverOnionV2,
      String serverOnionV3,
      NetworkParameters params,
      boolean ssl) {
    this.serverUrl = serverUrl;
    this.serverOnionV2 = serverOnionV2;
    this.serverOnionV3 = serverOnionV3;
    this.params = params;
    this.ssl = ssl;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public String getServerOnionV2() {
    return serverOnionV2;
  }

  public String getServerOnionV3() {
    return serverOnionV3;
  }

  public NetworkParameters getParams() {
    return params;
  }

  public boolean isSsl() {
    return ssl;
  }

  public static Optional<WhirlpoolServer> find(String value) {
    try {
      return Optional.of(valueOf(value));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
