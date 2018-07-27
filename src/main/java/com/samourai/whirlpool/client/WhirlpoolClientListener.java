package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public interface WhirlpoolClientListener {
    void success(int nbMixs);
    void fail(int currentMix, int nbMixs);
    void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess);
    void progress(int currentMix, int nbMixs, MixStatus mixStatus, int currentStep, int nbSteps);
}
