package com.samourai.whirlpool.client.mix.listener;

public enum MixFailReason {
  PROTOCOL_MISMATCH("Protocol mismatch (check for updates!)"),
  MIX_FAILED("Mix failed"),
  DISCONNECTED("Disconnected"),
  INPUT_REJECTED("Input rejected"),
  INTERNAL_ERROR("Internal error"),
  STOP("Stopped");

  private String message;

  MixFailReason(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
