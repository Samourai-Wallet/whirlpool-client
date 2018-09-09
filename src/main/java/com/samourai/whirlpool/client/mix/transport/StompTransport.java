package com.samourai.whirlpool.client.mix.transport;

import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;

/**
 * STOMP communication.
 */
public class StompTransport {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String HEADER_USERNAME = "user-name";

    private TransportListener listener;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private String stompUsername;
    private boolean done;

    public StompTransport(TransportListener listener) {
        this.listener = listener;
    }

    public String connect(String wsUrl, StompHeaders connectHeaders) throws Exception {
        stompClient = createWebSocketClient();
        stompSession = stompClient.connect(wsUrl, (WebSocketHttpHeaders) null, connectHeaders, computeStompSessionHandler()).get();
        done = false;

        String stompSessionId = stompSession.getSessionId();
        log = ClientUtils.prefixLogger(log, stompSessionId);

        return stompSessionId;
    }

    public void subscribe(StompHeaders subscribeHeaders, Consumer<Object> frameHandler, Consumer<String> errorHandler) {
        if (log.isDebugEnabled()) {
            log.debug("subscribe:" + subscribeHeaders.getDestination());
        }
        final StompHeaders completeHeaders = completeHeaders(subscribeHeaders);
        stompSession.subscribe(subscribeHeaders,
            new StompFrameHandler((payload) -> {
                if (!done) {
                    if (log.isDebugEnabled()) {
                        log.debug("--> (" + completeHeaders.getDestination() + ") " + ClientUtils.toJsonString(payload));
                    }
                    frameHandler.accept(payload);
                }
                else {
                    log.warn("frame ignored (done) (" + completeHeaders.getDestination() + "): " + ClientUtils.toJsonString(payload));
                }
            }, (error) -> {
                errorHandler.accept((String)error);
            })
        );
    }

    private StompSessionHandlerAdapter computeStompSessionHandler() {
        return new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                super.afterConnected(session, connectedHeaders);
                stompUsername = connectedHeaders.get(HEADER_USERNAME).iterator().next();
                if (log.isDebugEnabled()) {
                    log.debug("stompUsername=" + stompUsername);
                }
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                super.handleException(session, command, headers, payload, exception);
                log.error(" ! transportException",exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                super.handleTransportError(session, exception);
                if (exception instanceof ConnectionLostException) {
                    disconnect(true);
                    listener.onTransportConnectionLost(exception);
                }
                else {
                    log.error(" ! transportError : " + exception.getMessage());
                }
            }
        };
    }

    public void disconnect() {
        disconnect(false);
    }

    private void disconnect(boolean connectionLost) {
        this.done = true;

        // don't disconnect session if connectionLost, to avoid forever delays
        if (!connectionLost) {
            try {
                stompSession.disconnect();
            } catch(Exception e) {
                log.error("", e);
            }
        }
        stompSession = null;

        if (stompClient != null) {
            try {
                stompClient.stop();
            } catch(Exception e) {
                log.error("", e);
            }
            stompClient = null;
        }
    }

    // STOMP communication

    private WebSocketStompClient createWebSocketClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    public void send(String destination, Object message) {
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.setDestination(destination);
        stompHeaders = completeHeaders(stompHeaders);
        if (log.isDebugEnabled()) {
            log.debug("send: " + ClientUtils.toJsonString(message));
        }
        stompSession.send(stompHeaders, message);
    }

    private StompHeaders completeHeaders(StompHeaders stompHeaders) {
        stompHeaders.set(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
        return stompHeaders;
    }

    //

    public String getStompUsername() {
        return stompUsername;
    }
}
