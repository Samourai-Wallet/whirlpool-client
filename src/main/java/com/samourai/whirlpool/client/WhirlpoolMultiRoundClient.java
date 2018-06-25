package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.utils.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.v1.notifications.RoundStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class WhirlpoolMultiRoundClient {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private WhirlpoolClientConfig config;
    private int rounds;
    private int doneRounds;
    private String logPrefix;

    private List<WhirlpoolClient> whirlpoolClients;
    private WhirlpoolMultiRoundClientListener listener;

    public WhirlpoolMultiRoundClient(WhirlpoolClientConfig config) {
        this.config = config;
        this.logPrefix = null;
    }

    public void whirlpool(RoundParams roundParams, int rounds, WhirlpoolMultiRoundClientListener listener) {
        this.rounds = rounds;
        this.listener = listener;
        this.doneRounds = 0;
        this.whirlpoolClients = new ArrayList<>();

        new Thread(() -> {
            try {
                WhirlpoolClient whirlpoolClient = runClient(roundParams);

                synchronized (this) {
                    while(!whirlpoolClient.isDone()) {
                        wait(1000);
                    }
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }).start();

    }

    private WhirlpoolClient runClient(RoundParams roundParams) {
        WhirlpoolClientListener roundListener = computeRoundListener();

        WhirlpoolClient whirlpoolClient = new WhirlpoolClient(config);
        if (logPrefix != null) {
            int round = this.whirlpoolClients.size();
            whirlpoolClient.setLogPrefix(logPrefix+"["+(round+1)+"]");
        }
        whirlpoolClient.whirlpool(roundParams, roundListener);
        this.whirlpoolClients.add(whirlpoolClient);
        return whirlpoolClient;
    }

    private WhirlpoolClient getLastWhirlpoolClient() {
        return whirlpoolClients.get(whirlpoolClients.size() - 1);
    }

    private void onRoundSuccess() {
        listener.roundSuccess(doneRounds+1, rounds);

        this.doneRounds++;
        if (doneRounds == rounds) {
            // all rounds done
            listener.success(doneRounds);
        }
        else {
            // go to next round
            WhirlpoolClient whirlpoolClient = getLastWhirlpoolClient();
            RoundParams nextRoundParams = whirlpoolClient.computeNextRoundParams();
            runClient(nextRoundParams);
        }
    }

    private WhirlpoolClientListener computeRoundListener() {
        return new WhirlpoolClientListener() {
            @Override
            public void success() {
                onRoundSuccess();
            }

            @Override
            public void fail() {
                listener.fail(doneRounds+1, rounds);
            }

            @Override
            public void progress(RoundStatus roundStatus, int currentStep, int nbSteps) {
                listener.progress(doneRounds+1, rounds, roundStatus, currentStep, nbSteps);
            }
        };
    }

    public void exit() {
        WhirlpoolClient whirlpoolClient = getLastWhirlpoolClient();
        if (whirlpoolClient != null) {
            whirlpoolClient.exit();
        }
    }

    public void debugState() {
        if (log.isDebugEnabled()) {
            log.debug("Round "+doneRounds+"/"+rounds);
            for (WhirlpoolClient whirlpoolClient : whirlpoolClients) {
                whirlpoolClient.debugState();
            }
        }
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public WhirlpoolClient getClient(int round) {
        return whirlpoolClients.get(round-1);
    }

}
