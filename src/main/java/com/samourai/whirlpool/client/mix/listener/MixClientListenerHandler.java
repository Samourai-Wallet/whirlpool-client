package com.samourai.whirlpool.client.mix.listener;

import com.samourai.whirlpool.client.mix.MixParams;

public class MixClientListenerHandler {
  private MixClientListener mixClientListener;
  private static final int NB_STEPS = 10;

  public MixClientListenerHandler(MixClientListener mixClientListener) {
    this.mixClientListener = mixClientListener;
  }

  public void success(MixSuccess mixSuccess, MixParams nextMixParams) {
    mixClientListener.success(mixSuccess, nextMixParams);
  }

  public void fail(MixFailReason reason, String notifiableError) {
    mixClientListener.fail(reason, notifiableError);
  }

  public void progress(MixStep mixClientStatus) {
    int currentStep = 1;
    String info = "";

    switch (mixClientStatus) {
      case CONNECTING:
        info = "connecting...";
        currentStep = 1;
        break;
      case CONNECTED:
        info = "connected.";
        currentStep = 2;
        break;

      case REGISTERED_INPUT:
        info = "registered input. Waiting for a mix...";
        currentStep = 3;
        break;

      case CONFIRMING_INPUT:
        info = "trying to join a mix...";
        currentStep = 4;
        break;
      case CONFIRMED_INPUT:
        info = "joined a mix!";
        currentStep = 5;
        break;

      case REGISTERING_OUTPUT:
        info = "registering output...";
        currentStep = 6;
        break;
      case REGISTERED_OUTPUT:
        info = "registered output.";
        currentStep = 7;
        break;

      case SIGNING:
        info = "signing tx...";
        currentStep = 8;
        break;
      case SIGNED:
        info = "signed tx.";
        currentStep = 9;
        break;

      case REVEALED_OUTPUT:
        info =
            "revealed output. (mix failed, someone didn't register output. A new mix will start soon)";
        currentStep = 10;
        break;

      case SUCCESS:
        info = "mix success.";
        currentStep = 10;
        break;
      case FAIL:
        info = "mix failed.";
        currentStep = 10;
        break;
    }
    mixClientListener.progress(mixClientStatus, info, currentStep, NB_STEPS);
  }
}
