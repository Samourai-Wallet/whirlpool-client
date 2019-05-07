package com.samourai.whirlpool.client.whirlpool.listener;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
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

  private String format(String log) {
    return " - [MIX] " + log;
  }

  @Override
  public void success(MixSuccess mixSuccess) {
    super.success(mixSuccess);
    logInfo("⣿ WHIRLPOOL SUCCESS ⣿");

    logInfo(format("⣿ WHIRLPOOL SUCCESS ⣿ txid: " + mixSuccess.getReceiveUtxo().getHash()));
  }

  @Override
  public void fail(MixFailReason failReason, String notifiableError) {
    super.fail(failReason, notifiableError);
    String message = failReason.getMessage();
    if (notifiableError != null) {
      message += " ; " + notifiableError;
    }
    logError(format("⣿ WHIRLPOOL FAILED ⣿ " + message));
  }

  @Override
  public void progress(MixStep step) {
    super.progress(step);
    String asciiProgress = renderProgress(step.getProgress());
    logInfo(format(asciiProgress + " " + step + " : " + step.getMessage()));
  }

  private String renderProgress(int progressPercent) {
    StringBuilder progress = new StringBuilder();
    for (int i = 0; i < 100; i += 10) {
      progress.append(i < progressPercent ? "▮" : "▯");
    }
    progress.append(" (" + progressPercent + "%)");
    return progress.toString();
  }

  protected void logInfo(String message) {
    log.info(message);
  }

  protected void logError(String message) {
    log.error(message);
  }
}
