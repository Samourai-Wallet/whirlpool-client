package com.samourai.whirlpool.client;

import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;

public interface WhirlpoolMultiRoundClientListener {
    void success(int doneRounds);
    void fail(int currentRound, int nbRounds);
    void progress(int currentRound, int nbRounds, RoundStatus roundStatus, int currentStep, int nbSteps);
}
