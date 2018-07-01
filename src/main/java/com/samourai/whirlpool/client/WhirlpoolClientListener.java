package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.beans.RoundResultSuccess;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;

public interface WhirlpoolClientListener {
    void success(RoundResultSuccess roundResultSuccess);
    void fail();
    void progress(RoundStatus roundStatus, int currentStep, int nbSteps);
}
