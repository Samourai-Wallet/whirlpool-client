package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingWhirlpoolClientListener extends AbstractWhirlpoolClientListener {
  private Logger log = LoggerFactory.getLogger(LoggingWhirlpoolClientListener.class);

  public LoggingWhirlpoolClientListener(WhirlpoolClientListener notifyListener) {
    super(notifyListener);
  }

  public LoggingWhirlpoolClientListener() {
    super();
  }

  public void setLogPrefix(String logPrefix) {
    log = ClientUtils.prefixLogger(log, logPrefix);
  }

  private String format(int currentMix, int nbMixs, String log) {
    return " - [MIX " + currentMix + "/" + nbMixs + "] " + log;
  }

  @Override
  public void success(int nbMixs, MixSuccess mixSuccess) {
    super.success(nbMixs, mixSuccess);
    logInfo("⣿ WHIRLPOOL SUCCESS ⣿");
  }

  @Override
  public void fail(int currentMix, int nbMixs) {
    super.fail(currentMix, nbMixs);
    logError(format(currentMix, nbMixs, "⣿ WHIRLPOOL FAILED ⣿ Check logs for errors."));
  }

  @Override
  public void progress(
      int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
    super.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
    String asciiProgress = renderProgress(stepNumber, nbSteps);
    logInfo(format(currentMix, nbMixs, asciiProgress + " " + step + " : " + stepInfo));
  }

  private String renderProgress(int stepNumber, int nbSteps) {
    StringBuilder progress = new StringBuilder();
    for (int i = 0; i < nbSteps; i++) {
      progress.append(i < stepNumber ? "▮" : "▯");
    }
    progress.append(" (" + stepNumber + "/" + nbSteps + ")");
    return progress.toString();
  }

  @Override
  public void mixSuccess(int currentMix, int nbMixs, MixSuccess mixSuccess) {
    super.mixSuccess(currentMix, nbMixs, mixSuccess);
    logInfo(
        format(
            currentMix,
            nbMixs,
            "SUCCESS - Funds sent to "
                + mixSuccess.getReceiveAddress()
                + ", txid: "
                + mixSuccess.getReceiveUtxo().getHash()));
  }

  protected void logInfo(String message) {
    log.info(message);
  }

  protected void logError(String message) {
    log.error(message);
  }
}
