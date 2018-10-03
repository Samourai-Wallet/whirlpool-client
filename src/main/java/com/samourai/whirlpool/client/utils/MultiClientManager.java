package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.WhirlpoolClient;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.MessageHandler;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class MultiClientManager {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private MessageHandler.Whole<Boolean> onDone;

    private List<WhirlpoolClient> clients;
    private List<MultiClientListener> listeners;
    private boolean done;

    public MultiClientManager() {
        this(null);
    }

    public MultiClientManager(MessageHandler.Whole<Boolean> onDone) {
        this.onDone = onDone;

        clients = new ArrayList<>();
        listeners = new ArrayList<>();
    }

    public MultiClientListener register(WhirlpoolClient whirlpoolClient) {
        int i=clients.size()+1;
        log.info("Register client#"+i);
        MultiClientListener listener = new MultiClientListener(this);
        listener.setLogPrefix("client#"+i);
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

    public synchronized void waitMix() {
        do {
            checkClients();
            try {
                wait();
            } catch (Exception e) {}
        } while( !done);
    }

    private void checkClients() {
        boolean success = true;
        for (int i=0; i<clients.size(); i++) {
            MultiClientListener listener = listeners.get(i);
            if (listener == null) {
                success = false;
                log.debug("Client#" + i + ": null");
            } else {
                log.debug("Client#" + i + ": mixStatus=" + listener.getMixStatus() + ", mixStep=" + listener.getMixStep());
                if (MixStatus.FAIL.equals(listener.getMixStatus())) {
                    // 1 client failed
                    this.done = true;
                    if (onDone != null) {
                        onDone.onMessage(false);
                    }
                    return;
                }
                if (!MixStatus.SUCCESS.equals(listener.getMixStatus())) {
                    success = false;
                }
            }
        }
        // all clients success
        if (success) {
            this.done = true;
            if (onDone != null) {
                onDone.onMessage(true);
            }
            return;
        }
    }

    private void debugClients() {
        if (log.isDebugEnabled()) {
            log.debug("%%% debugging clients states... %%%");
            int i=0;
            for (WhirlpoolClient whirlpoolClient : clients) {
                if (whirlpoolClient != null) {
                    MultiClientListener listener = listeners.get(i);
                    log.debug("Client#" + i + ": mixStatus=" + listener.getMixStatus()+", mixStep=" + listener.getMixStep());
                }
                i++;
            }
        }
    }

    public MultiClientListener getListener(int i) {
        return listeners.get(i);
    }

    public boolean isDone() {
        checkClients();
        return done;
    }
}
