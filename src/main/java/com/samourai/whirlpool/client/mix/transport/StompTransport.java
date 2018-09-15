package com.samourai.whirlpool.client.mix.transport;

import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.MessageHandler;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

/**
 * STOMP communication.
 */
public class StompTransport {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String HEADER_USERNAME = "user-name";
    public static final String HEADER_DESTINATION = "destination";

    private IWhirlpoolStompClient stompClient;
    private TransportListener listener;

    private boolean done;

    public StompTransport(IWhirlpoolStompClient stompClient, TransportListener listener) {
        this.stompClient = stompClient;
        this.listener = listener;
    }

    public String connect(String wsUrl, Map<String,String> connectHeaders) {
        done = false;
        stompClient.connect(wsUrl, connectHeaders, new MessageHandler.Whole<String>(){
            @Override
            public void onMessage(String stompUsername) {
                if (log.isDebugEnabled()) {
                    log.debug("stompUsername=" + stompUsername);
                }
                listener.onTransportConnected(stompUsername);
            }
        }, new MessageHandler.Whole<Throwable>(){
            @Override
            public void onMessage(Throwable exception) {
                done = true;
                listener.onTransportDisconnected(exception);
            }
        });

        String stompSessionId = stompClient.getSessionId();
        log = ClientUtils.prefixLogger(log, stompSessionId);

        return stompSessionId;
    }

    public void subscribe(Map<String,String> subscribeHeaders, final MessageHandler.Whole<Object> frameHandler, MessageHandler.Whole<String> errorHandler) {
        if (log.isDebugEnabled()) {
            log.debug("subscribe:" + subscribeHeaders.get(HEADER_DESTINATION));
        }
        final Map<String,String> completeHeaders = completeHeaders(subscribeHeaders);

        MessageHandler.Whole<Object> onMessage = new MessageHandler.Whole<Object>() {
            @Override
            public void onMessage(Object payload) {
                if (!done) {
                    if (log.isDebugEnabled()) {
                        log.debug("--> (" + completeHeaders.get(HEADER_DESTINATION) + ") " + ClientUtils.toJsonString(payload));
                    }
                    frameHandler.onMessage(payload);
                }
                else {
                    log.warn("frame ignored (done) (" + completeHeaders.get(HEADER_DESTINATION) + "): " + ClientUtils.toJsonString(payload));
                }
            }
        };

        stompClient.subscribe(subscribeHeaders, onMessage, errorHandler);
    }

    public void disconnect() {
        this.done = true;
        try {
            stompClient.disconnect();
        } catch(Exception e) {
            log.error("", e);
        }
    }

    // STOMP communication

    public void send(String destination, Object message) {
        Map<String,String> stompHeaders = new HashMap<>();
        stompHeaders.put(HEADER_DESTINATION, destination);
        stompHeaders = completeHeaders(stompHeaders);
        if (log.isDebugEnabled()) {
            log.debug("send: " + ClientUtils.toJsonString(message));
        }
        stompClient.send(stompHeaders, message);
    }

    private Map<String,String> completeHeaders(Map<String,String> stompHeaders) {
        stompHeaders.put(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
        return stompHeaders;
    }
}
