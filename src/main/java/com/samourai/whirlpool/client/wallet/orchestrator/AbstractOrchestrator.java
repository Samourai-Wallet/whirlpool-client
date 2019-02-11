package com.samourai.whirlpool.client.wallet.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(AbstractOrchestrator.class);
  private final int LOOP_DELAY;

  private boolean started;
  protected Thread myThread;

  public AbstractOrchestrator(int loopDelay) {
    this.LOOP_DELAY = loopDelay;
    resetOrchestrator();
  }

  protected void resetOrchestrator() {}

  public synchronized void start() {
    if (isStarted()) {
      log.error("Cannot start: already started");
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("Starting...");
    }
    this.started = true;
    this.myThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                doRun();
              }
            });
    this.myThread.start();
  }

  private void doRun() {
    while (started) {
      try {
        runOrchestrator();
        synchronized (myThread) {
          myThread.wait(LOOP_DELAY);
        }
      } catch (InterruptedException e) {
        // normal
      }
    }
    this.myThread = null;
    if (log.isDebugEnabled()) {
      log.debug("Ended.");
    }
    resetOrchestrator();
  }

  protected abstract void runOrchestrator();

  public synchronized void stop() {
    if (!isStarted()) {
      log.error("Cannot stop: not started");
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("Ending...");
    }
    synchronized (myThread) {
      this.started = false;
      myThread.notify();
      myThread = null;
    }
  }

  protected void notifyOrchestrator() {
    if (myThread != null) {
      synchronized (myThread) {
        myThread.notify();
      }
    }
  }

  public boolean isStarted() {
    return started;
  }
}
