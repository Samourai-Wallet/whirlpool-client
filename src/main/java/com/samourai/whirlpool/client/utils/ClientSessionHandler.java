package com.samourai.whirlpool.client.utils;

import com.samourai.whirlpool.client.WhirlpoolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.*;

import java.lang.invoke.MethodHandles;

/**
 * STOMP events interception.
 */
public class ClientSessionHandler extends StompSessionHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WhirlpoolClient whirlpoolClient;

    public ClientSessionHandler(WhirlpoolClient whirlpoolClient) {
        this.whirlpoolClient = whirlpoolClient;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        super.afterConnected(session, connectedHeaders);
        String username = connectedHeaders.get("user-name").iterator().next();
        this.whirlpoolClient.onAfterConnected(username);
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        super.handleException(session, command, headers, payload, exception);
        log.error("sessionException",exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        super.handleTransportError(session, exception);
        whirlpoolClient.onTransportError(exception);
    }
}
