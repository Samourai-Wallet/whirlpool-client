package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public interface MixClientListener {
    void success(MixSuccess mixSuccess);
    void fail();
    void progress(MixStatus mixStatus, int currentStep, int nbSteps);
}
