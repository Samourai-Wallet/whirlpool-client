package com.samourai.whirlpool.client.mix.listener;

public enum MixStep {
  CONNECTING("connecting...", 10),
  CONNECTED("connected", 20),

  REGISTERED_INPUT("waiting for a mix...", 30),

  CONFIRMING_INPUT("trying to join a mix...", 40),
  CONFIRMED_INPUT("joined a mix!", 50),

  REGISTERING_OUTPUT("registering output", 60),
  REGISTERED_OUTPUT("registered output", 70),

  REVEALED_OUTPUT("mix failed: someone didn't register output", 100),

  SIGNING("signing", 80),
  SIGNED("signed", 90),

  SUCCESS("mix success", 100),
  FAIL("mix failed", 100);

  private String message;
  private int progress;

  MixStep(String message, int progress) {
    this.message = message;
    this.progress = progress;
  }

  public String getMessage() {
    return message;
  }

  public int getProgress() {
    return progress;
  }
}
