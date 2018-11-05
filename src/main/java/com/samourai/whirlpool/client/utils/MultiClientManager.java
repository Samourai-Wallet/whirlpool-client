package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientImpl;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiClientManager {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected List<WhirlpoolClient> clients;
  protected List<MultiClientListener> listeners;

  public MultiClientManager() {
    clients = new ArrayList<>();
    listeners = new ArrayList<>();
  }

  public synchronized MultiClientListener register(WhirlpoolClient whirlpoolClient) {
    return register(whirlpoolClient, 0);
  }

  public synchronized MultiClientListener register(
      WhirlpoolClient whirlpoolClient, int missedMixs) {
    int i = clients.size() + 1;
    log.info("Register client#" + i);
    ((WhirlpoolClientImpl) whirlpoolClient).setLogPrefix("[client#" + i + "]");
    MultiClientListener listener = new MultiClientListener(this, missedMixs);
    listener.setLogPrefix("client#" + i);
    this.clients.add(whirlpoolClient);
    this.listeners.add(listener);
    return listener;
  }

  public void exit() {
    for (WhirlpoolClient whirlpoolClient : clients) {
      if (whirlpoolClient != null) {
        whirlpoolClient.exit();
      }
    }
  }

  /** @return true when success, false when failed */
  public synchronized boolean waitDone(int currentMix, int nbSuccessExpected) {
    do {
      if (isDone(currentMix, nbSuccessExpected)) {
        return (getNbSuccess(currentMix) != null);
      }

      // will be notified by listeners to wakeup
      try {
        if (log.isDebugEnabled()) {
          Integer nbSuccess = getNbSuccess(currentMix);
          log.debug("waitDone... (nbSuccess=" + nbSuccess + "/" + nbSuccessExpected + ")");
        }
        wait();
      } catch (Exception e) {
      }
    } while (true);
  }

  /** @return true when success, false when failed */
  public synchronized boolean waitDone() {
    return waitDone(1, clients.size());
  }

  public boolean isDone() {
    return isDone(1, clients.size());
  }

  public boolean isDone(int currentMix, int nbSuccessExpected) {
    Integer nbSuccess = getNbSuccess(currentMix);
    return (nbSuccess == null || nbSuccess == nbSuccessExpected);
  }

  protected void debugClients(int currentMix) {
    if (log.isDebugEnabled()) {
      log.debug("%%% debugging clients states for mix #" + currentMix + "... %%%");
      int i = 0;
      for (WhirlpoolClient whirlpoolClient : clients) {
        if (whirlpoolClient != null) {
          MultiClientListener listener = listeners.get(i);
          log.debug(
              "Client#"
                  + i
                  + ": mixStatus="
                  + listener.getMixStatus(currentMix)
                  + ", mixStep="
                  + listener.getMixStep(currentMix));
        } else {
          log.debug("Client#" + i + ": NULL");
        }
        i++;
      }
    }
  }

  public MultiClientListener getListener(int i) {
    return listeners.get(i);
  }

  /** @return number of success clients, or null=1 client failed */
  public Integer getNbSuccess(int currentMix) {
    if (clients.isEmpty()) {
      return 0;
    }

    int nbSuccess = 0;
    for (int i = 0; i < clients.size(); i++) {
      MultiClientListener listener = listeners.get(i);
      if (listener == null) {
        // client not initialized => not done
        log.debug("Client#" + i + "[" + currentMix + "]: null");
      } else {
        log.debug(
            "Client#"
                + i
                + "["
                + currentMix
                + "]: mixStatus="
                + listener.getMixStatus(currentMix)
                + ", mixStep="
                + listener.getMixStep(currentMix));
        if (MixStatus.FAIL.equals(listener.getMixStatus(currentMix))) {
          // client failed
          return null;
        }
        if (MixStatus.SUCCESS.equals(listener.getMixStatus(currentMix))) {
          // client success
          nbSuccess++;
        }
      }
    }
    // all clients success
    return nbSuccess;
  }
}
