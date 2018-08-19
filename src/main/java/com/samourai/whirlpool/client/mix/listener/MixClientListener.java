package com.samourai.whirlpool.client.mix.listener;

public interface MixClientListener {
    void success(MixSuccess mixSuccess);
    void fail();
    void progress(MixStep step, String stepInfo, int stepNumber, int nbSteps);
}
