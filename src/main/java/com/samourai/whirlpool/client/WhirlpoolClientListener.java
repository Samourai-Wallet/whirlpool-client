package com.samourai.whirlpool.client;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;

public interface WhirlpoolClientListener {
    void success();
    void fail();
    void progress(RoundStatus roundStatus, int currentStep, int nbSteps);
}
