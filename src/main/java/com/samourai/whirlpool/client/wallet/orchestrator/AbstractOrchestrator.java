package com.samourai.whirlpool.client.wallet.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOrchestrator {
  private final Logger log = LoggerFactory.getLogger(AbstractOrchestrator.class);
  private final int LOOP_DELAY;

  private boolean started;
  protected Thread myThread;
  private boolean dontDisturb;

  public AbstractOrchestrator(int loopDelay) {
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
    if (!dontDisturb) {
      if (myThread != null) {
        if (log.isDebugEnabled()) {
          log.debug("Notifying...");
        }
        synchronized (myThread) {
          myThread.notify();
        }
      } else {
        log.error("Notifying failed, myThread=null");
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("NOT notifying (dontDisturb)");
      }
    }
  }

  protected void sleepOrchestrator(long timeToWait, boolean withDontDisturb) {
    try {
      synchronized (myThread) {
        if (withDontDisturb) {
          dontDisturb = true;
        }
        if (log.isDebugEnabled()) {
          log.debug("sleepOrchestrator... withDontDisturb=" + withDontDisturb);
        }
        myThread.wait(timeToWait);
        if (withDontDisturb) {
          dontDisturb = false;
        }
      }
    } catch (InterruptedException e) {
    }
  }

  public boolean isStarted() {
    return started;
  }

  public boolean isDontDisturb() {
    return dontDisturb;
  }
}
