package com.samourai.whirlpool.client.whirlpool;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.mix.MixClient;
import com.samourai.whirlpool.client.mix.MixParams;
import com.samourai.whirlpool.client.mix.listener.MixClientListener;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.whirlpool.listener.WhirlpoolClientListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhirlpoolClientImpl implements WhirlpoolClient {
  private Logger log;

  private WhirlpoolClientConfig config;

  private boolean done;
  private String logPrefix;

  private MixClient mixClient;
  private Thread mixThread;
  private WhirlpoolClientListener listener;

  /**
   * Get a new Whirlpool client.
   *
   * @param config client configuration (server...)
   * @return
   */
  public static WhirlpoolClient newClient(WhirlpoolClientConfig config) {
    String logPrefix = Long.toString(System.currentTimeMillis());
    return new WhirlpoolClientImpl(config, logPrefix);
  }

  private WhirlpoolClientImpl(WhirlpoolClientConfig config, String logPrefix) {
    this.log = LoggerFactory.getLogger(WhirlpoolClientImpl.class + "[" + logPrefix + "]");
    this.config = config;
    this.logPrefix = logPrefix;
    if (log.isDebugEnabled()) {
      log.debug("+whirlpoolClient");
    }
  }

  @Override
  public void whirlpool(final MixParams mixParams, WhirlpoolClientListener listener) {
    this.listener = listener;

    this.mixThread =
        new Thread(
            new Runnable() {
              @Override
              public synchronized void run() {
                runClient(mixParams);
                while (!done) {
                  try {
                    synchronized (mixThread) {
                      mixThread.wait();
                    }
                  } catch (Exception e) {
                  }
                }
              }
            },
            "whirlpoolClient-" + logPrefix);
    this.mixThread.start();
  }

  private void runClient(MixParams mixParams) {
    MixClientListener mixListener = computeMixListener();

    mixClient = new MixClient(config, logPrefix);
    mixClient.whirlpool(mixParams, mixListener);
  }

  private MixClientListener computeMixListener() {
    return new MixClientListener() {
      @Override
      public void success(MixSuccess mixSuccess) {
        // done
        listener.success(mixSuccess);
        exit();
      }

      @Override
      public void fail(MixFailReason reason, String notifiableError) {
        listener.fail(reason, notifiableError);
        exit();
      }

      @Override
      public void progress(MixStep step) {
        listener.progress(step);
      }
    };
  }

  @Override
  public void exit() {
    if (!done) {
      if (log.isDebugEnabled()) {
        log.debug("--whirlpoolClient");
      }
      done = true;
      if (mixClient != null) {
        mixClient.exit();
      }
      if (mixThread != null) {
        synchronized (mixThread) {
          mixThread.notify();
        }
      }
    }
  }

  public MixClient getMixClient() {
    return mixClient;
  }
}
