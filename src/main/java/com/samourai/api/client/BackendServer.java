package com.samourai.api.client;

public enum BackendServer {
  MAINNET(
      "https://api.samouraiwallet.com",
      "d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion"),
  TESTNET(
      "https://api.samouraiwallet.com/test",
      "d2oagweysnavqgcfsfawqwql2rwxend7xxpriq676lzsmtfwbt75qbqd.onion/test");

  private String backendUrl;
  private String backendUrlOnion;

  BackendServer(String backendUrl, String backendUrlOnion) {
    this.backendUrl = backendUrl;
    this.backendUrlOnion = backendUrlOnion;
  }

  public String getBackendUrl(boolean onion) {
    return onion ? backendUrlOnion : backendUrl;
  }
}
