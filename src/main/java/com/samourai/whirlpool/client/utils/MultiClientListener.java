package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.whirlpool.listener.LoggingWhirlpoolClientListener;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;

public class MultiClientListener extends LoggingWhirlpoolClientListener {
    private MixStatus mixStatus;
    private MixStep mixStep;
    private MultiClientManager multiClientManager;

    public MultiClientListener(MultiClientManager multiClientManager) {
        this.multiClientManager = multiClientManager;
    }

    @Override
    public void success(int nbMixs, MixSuccess mixSuccess) {
        super.success(nbMixs, mixSuccess);
        this.mixStatus = MixStatus.SUCCESS;
        notifyMultiClientManager();
    }

    @Override
    public void progress(int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
        super.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
        this.mixStep = step;
    }

    @Override
    public void fail(int currentMix, int nbMixs) {
        super.fail(currentMix, nbMixs);
        this.mixStatus = MixStatus.FAIL;
        notifyMultiClientManager();
    }

    @Override
    public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
        super.mixSuccess(currentMix, nbMixs, mixSuccess);
        this.mixStatus = MixStatus.SUCCESS;
    }

    private void notifyMultiClientManager() {
        synchronized (multiClientManager) {
            multiClientManager.notify();
        }
    }

    public MixStatus getMixStatus() {
        return mixStatus;
    }

    public MixStep getMixStep() {
        return mixStep;
    }
}