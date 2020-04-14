package com.samourai.http.client;

public enum HttpUsage {
  BACKEND(true),
  COORDINATOR_REST(true),
  COORDINATOR_WEBSOCKET(false),
  COORDINATOR_REGISTER_OUTPUT(true);

  private boolean rest;

  private HttpUsage(boolean rest) {
    this.rest = rest;
  }

  public boolean isRest() {
    return rest;
  }
}
