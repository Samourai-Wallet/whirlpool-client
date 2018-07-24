package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.client.mix.MixClient;
import com.samourai.whirlpool.client.mix.MixClientListener;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.utils.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.v1.notifications.MixStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class WhirlpoolClient {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private WhirlpoolClientConfig config;
    private int rounds;
    private int doneRounds;
    private String logPrefix;

    private List<MixClient> mixClients;
    private WhirlpoolClientListener listener;

    public WhirlpoolClient(WhirlpoolClientConfig config) {
        this.config = config;
        this.logPrefix = null;
    }

    public void whirlpool(MixParams mixParams, int rounds, WhirlpoolClientListener listener) {
        this.rounds = rounds;
        this.listener = listener;
        this.doneRounds = 0;
        this.mixClients = new ArrayList<>();

        new Thread(() -> {
            try {
                MixClient mixClient = runClient(mixParams);

                synchronized (this) {
                    while(!mixClient.isDone()) {
                        wait(1000);
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }).start();

    }

    private MixClient runClient(MixParams mixParams) {
        MixClientListener roundListener = computeRoundListener();

        MixClient mixClient = new MixClient(config);
        if (logPrefix != null) {
            int round = this.mixClients.size();
            mixClient.setLogPrefix(logPrefix+"["+(round+1)+"]");
        }
        mixClient.whirlpool(mixParams, roundListener);
        this.mixClients.add(mixClient);
        return mixClient;
    }

    private MixClient getLastWhirlpoolClient() {
        return mixClients.get(mixClients.size() - 1);
    }

    private void onRoundSuccess(MixSuccess mixSuccess) {
        listener.mixSuccess(doneRounds+1, rounds, mixSuccess);

        this.doneRounds++;
        if (doneRounds == rounds) {
            // all rounds done
            listener.success(doneRounds);
        }
        else {
            // go to next round
            MixClient mixClient = getLastWhirlpoolClient();
            MixParams nextMixParams = mixClient.computeNextRoundParams();
            runClient(nextMixParams);
        }
    }

    private MixClientListener computeRoundListener() {
        return new MixClientListener() {
            @Override
            public void success(MixSuccess mixSuccess) {
                onRoundSuccess(mixSuccess);
            }

            @Override
            public void fail() {
                listener.fail(doneRounds+1, rounds);
            }

            @Override
            public void progress(MixStatus mixStatus, int currentStep, int nbSteps) {
                listener.progress(doneRounds+1, rounds, mixStatus, currentStep, nbSteps);
            }
        };
    }

    public void exit() {
        MixClient mixClient = getLastWhirlpoolClient();
        if (mixClient != null) {
            mixClient.exit();
        }
    }

    public void debugState() {
        if (log.isDebugEnabled()) {
            log.debug("Round "+doneRounds+"/"+rounds);
            for (MixClient mixClient : mixClients) {
                mixClient.debugState();
            }
        }
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public MixClient getMixClient(int round) {
        return mixClients.get(round-1);
    }

}
