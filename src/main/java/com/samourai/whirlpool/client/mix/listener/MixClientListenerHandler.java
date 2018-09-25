package com.samourai.whirlpool.client.mix.listener;

import com.samourai.whirlpool.client.mix.MixParams;

public class MixClientListenerHandler {
    private MixClientListener mixClientListener;

    public MixClientListenerHandler(MixClientListener mixClientListener) {
        this.mixClientListener = mixClientListener;
    }

    public void success(MixSuccess mixSuccess, MixParams nextMixParams) {
        mixClientListener.success(mixSuccess, nextMixParams);
    }

    public void fail() {
        mixClientListener.fail();
    }

    public void progress(MixStep mixClientStatus) {
        int currentStep = 1;
        int nbSteps = 9;
        String info = "";

        switch(mixClientStatus) {
            case CONNECTING:
                info = "connecting...";
                currentStep = 1;
                break;
            case CONNECTED:
                info = "connected.";
                currentStep = 2;
                break;

            case REGISTERING_INPUT:
                info = "registering input...";
                currentStep = 3;
                break;
            case QUEUED_INPUT:
                info = "input queued, waiting for next mix...";
                currentStep = 3;
                break;
            case REGISTERED_INPUT:
                info = "joined mix (input registered).";
                currentStep = 4;
                break;

            case REGISTERING_OUTPUT:
                info = "registering output...";
                currentStep = 5;
                break;
            case REGISTERED_OUTPUT:
                info = "registered output.";
                currentStep = 6;
                break;

            case SIGNING:
                info = "signing tx...";
                currentStep = 7;
                break;
            case SIGNED:
                info = "signed tx.";
                currentStep = 8;
                break;

            case REVEALING_OUTPUT:
                info = "revealing output... (mix failed, someone didn't register output)";
                currentStep = 9;
                break;
            case REVEALED_OUTPUT:
                info = "revealed output. (mix failed, a new mix will start soon)";
                currentStep = 9;
                break;


            case SUCCESS:
                info = "mix success.";
                currentStep = 9;
                break;
            case FAIL:
                info = "mix failed.";
                currentStep = 9;
                break;
        }
        mixClientListener.progress(mixClientStatus, info, currentStep, nbSteps);
    }

}
