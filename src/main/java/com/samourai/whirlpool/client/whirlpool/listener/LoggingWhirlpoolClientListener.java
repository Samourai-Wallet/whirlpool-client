package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class LoggingWhirlpoolClientListener implements WhirlpoolClientListener {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public LoggingWhirlpoolClientListener() {
    }

    public void setLogPrefix(String logPrefix) {
        log = ClientUtils.prefixLogger(log, logPrefix);
    }

    private String format(int currentMix, int nbMixs, String log) {
        return " • [MIX " + currentMix + "/" + nbMixs + "] " + log;
    }

    @Override
    public void success(int nbMixs, MixSuccess mixSuccess) {
        log.info("⣿ WHIRLPOOL SUCCESS ⣿ Funds will be received at " + mixSuccess.getReceiveAddress() + ", utxo " + mixSuccess.getReceiveUtxoHash() + ":" + mixSuccess.getReceiveUtxoIdx());
    }

    @Override
    public void fail(int currentMix, int nbMixs) {
        log.info("⣿ WHIRLPOOL FAILED ⣿ Mix "+currentMix+"/"+nbMixs+" failed. Check logs for errors.");
    }

    @Override
    public void progress(int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
        String asciiProgress = renderProgress(stepNumber, nbSteps);
        log.info(format(currentMix, nbMixs, asciiProgress + " " + step + " : " + stepInfo));
    }

    private String renderProgress(int stepNumber, int nbSteps) {
        StringBuilder progress = new StringBuilder();
        for (int i=0; i<nbSteps; i++) {
            progress.append(i < stepNumber ? "▮" : "▯");
        }
        progress.append(" (" + stepNumber + "/" + nbSteps + ")");
        return progress.toString();
    }

    @Override
    public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
        log.info(format(currentMix, nbMixs, "SUCCESS - Funds will be received at " + mixSuccess.getReceiveAddress() + ", utxo " + mixSuccess.getReceiveUtxoHash() + ":" + mixSuccess.getReceiveUtxoIdx()));
    }
}
