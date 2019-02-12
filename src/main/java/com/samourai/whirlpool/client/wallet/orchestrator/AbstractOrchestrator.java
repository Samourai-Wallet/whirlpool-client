package com.samourai.whirlpool.client.wallet.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOrchestrator {
  private Logger log;
  private final int LOOP_DELAY;

  private boolean started;
  protected Thread myThread;
  private boolean dontDisturb;

  public AbstractOrchestrator(int loopDelay, String orchestratorName) {
    this.log = LoggerFactory.getLogger(orchestratorName);
    this.LOOP_DELAY = loopDelay;
    resetOrchestrator();
  }

  protected void resetOrchestrator() {
    this.dontDisturb = false;
  }

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
                while (started) {
                  runOrchestrator();

                  // orchestrator may have been stopped in the meantime, as function is not
                  // synchronized
                  doSleep(LOOP_DELAY);
                }

                // thread exiting
                myThread = null;
                if (log.isDebugEnabled()) {
                  log.debug("Ended.");
                }
                resetOrchestrator();
              }
            });
    this.myThread.start();
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
    }
  }

  protected synchronized void notifyOrchestrator() {
    if (isStarted() && !isDontDisturb()) {
      if (log.isDebugEnabled()) {
        log.debug("Notifying...");
      }
      synchronized (myThread) {
        myThread.notify();
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("NOT notifying (dontDisturb)");
      }
    }
  }

  protected synchronized void sleepOrchestrator(long timeToWait, boolean withDontDisturb) {
    if (withDontDisturb) {
      dontDisturb = true;
    }
    doSleep(timeToWait);
    if (withDontDisturb) {
      dontDisturb = false;
    }
  }

  private void doSleep(long timeToWait) {
    if (log.isDebugEnabled()) {
      log.debug("doSleep");
    }
    try {
      synchronized (myThread) {
        myThread.wait(timeToWait);
      }
    } catch (InterruptedException e) {
    }
    if (log.isDebugEnabled()) {
      log.debug("waking up from doSleep");
    }
  }

  public boolean isStarted() {
    return started;
  }

  public boolean isDontDisturb() {
    return dontDisturb;
  }
}
