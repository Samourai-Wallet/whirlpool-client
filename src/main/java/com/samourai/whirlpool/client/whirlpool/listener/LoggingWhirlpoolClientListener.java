package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingWhirlpoolClientListener implements WhirlpoolClientListener {
  private Logger log = LoggerFactory.getLogger(LoggingWhirlpoolClientListener.class);
  private WhirlpoolClientListener notifyListener;

  public LoggingWhirlpoolClientListener(WhirlpoolClientListener notifyListener) {
    this.notifyListener = notifyListener;
  }

  public LoggingWhirlpoolClientListener() {
    this(null);
  }

  public void setLogPrefix(String logPrefix) {
    log = ClientUtils.prefixLogger(log, logPrefix);
  }

  private String format(int currentMix, int nbMixs, String log) {
    return " - [MIX " + currentMix + "/" + nbMixs + "] " + log;
  }

  @Override
  public void success(int nbMixs, MixSuccess mixSuccess) {
    logInfo("⣿ WHIRLPOOL SUCCESS ⣿");

    if (notifyListener != null) {
      notifyListener.success(nbMixs, mixSuccess);
    }
  }

  @Override
  public void fail(int currentMix, int nbMixs) {
    logError(format(currentMix, nbMixs, "⣿ WHIRLPOOL FAILED ⣿ Check logs for errors."));

    if (notifyListener != null) {
      notifyListener.fail(currentMix, nbMixs);
    }
  }

  @Override
  public void progress(
      int currentMix, int nbMixs, MixStep step, String stepInfo, int stepNumber, int nbSteps) {
    String asciiProgress = renderProgress(stepNumber, nbSteps);
    logInfo(format(currentMix, nbMixs, asciiProgress + " " + step + " : " + stepInfo));

    if (notifyListener != null) {
      notifyListener.progress(currentMix, nbMixs, step, stepInfo, stepNumber, nbSteps);
    }
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
    logInfo(
        format(
            currentMix,
            nbMixs,
            "SUCCESS - Funds sent to "
                + mixSuccess.getReceiveAddress()
                + ", utxo "
                + mixSuccess.getReceiveUtxo().getHash()
                + ":"
                + mixSuccess.getReceiveUtxo().getIndex()));

    if (notifyListener != null) {
      notifyListener.mixSuccess(currentMix, nbMixs, mixSuccess);
    }
  }

  protected void logInfo(String message) {
    log.info(message);
  }

  protected void logError(String message) {
    log.error(message);
  }
}
