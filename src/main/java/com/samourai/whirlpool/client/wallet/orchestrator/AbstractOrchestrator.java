package com.samourai.whirlpool.client.wallet.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOrchestrator {
  private Logger log;
  private final int LOOP_DELAY;
  private final int START_DELAY;

  private boolean started;
  protected Thread myThread;
  private boolean dontDisturb;
  private long lastRun;

  public AbstractOrchestrator(int loopDelay) {
    this(loopDelay, 0);
  }

  public AbstractOrchestrator(int loopDelay, int startDelay) {
    this.log = LoggerFactory.getLogger(getClass().getName());
    this.LOOP_DELAY = loopDelay;
    this.START_DELAY = startDelay;
    resetOrchestrator();
  }

  protected void resetOrchestrator() {
    this.dontDisturb = false;
    this.lastRun = 0;
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
                if (START_DELAY > 0) {
                  doSleep(START_DELAY);
                }
                while (started) {
                  runOrchestrator();

                  // orchestrator may have been stopped in the meantime, as function is not
                  // synchronized
                  doSleep(LOOP_DELAY);
                }

                // thread exiting
                myThread = null;
                if (log.isDebugEnabled()) {
                  log.debug("Ended. started=" + started);
                }
                resetOrchestrator();
              }
            },
            getClass().getSimpleName());
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
    try {
      synchronized (myThread) {
        myThread.wait(timeToWait);
      }
    } catch (InterruptedException e) {
    }
  }

  private long computeWaitForLastRunDelay(int delay) {
    long elapsedTimeSinceLastRun = System.currentTimeMillis() - lastRun;
    long timeToWait = (delay * 1000) - elapsedTimeSinceLastRun;
    return timeToWait;
  }

  protected boolean waitForLastRunDelay(int delay, String logMessage) {
    long timeToWait = computeWaitForLastRunDelay(delay);
    if (timeToWait > 0) {
      if (log.isDebugEnabled()) {
        log.debug(logMessage + " (" + (timeToWait / 1000) + "s to wait)");
      }
      sleepOrchestrator(timeToWait, true);
      return true;
    }
    return false;
  }

  protected void setLastRun() {
    this.lastRun = System.currentTimeMillis();
  }

  public boolean isStarted() {
    return started;
  }

  public boolean isDontDisturb() {
    return dontDisturb;
  }
}
