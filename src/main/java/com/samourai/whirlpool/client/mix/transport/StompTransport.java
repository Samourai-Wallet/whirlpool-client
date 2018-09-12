package com.samourai.whirlpool.client.mix.transport;

import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * STOMP communication.
 */
public class StompTransport {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String HEADER_USERNAME = "user-name";
    public static final String HEADER_DESTINATION = "destination";

    private TransportListener listener;

    private StompClient stompClient;
    private String stompUsername;
    private boolean done;

    public StompTransport(TransportListener listener) {
        this.listener = listener;
    }

    public String connect(String wsUrl, Map<String,String> connectHeaders) throws Exception {
        Consumer<String> onConnect = (stompUsername) -> {
            this.stompUsername = stompUsername;
            if (log.isDebugEnabled()) {
                log.debug("stompUsername=" + stompUsername);
            }
        };
        Consumer<Exception> onDisconnect = (exception) -> {
            disconnect(true);
            listener.onTransportConnectionLost(exception);
        };
        stompClient.connect(wsUrl, connectHeaders, onConnect, onDisconnect);
        done = false;

        String stompSessionId = stompClient.getSessionId();
        log = ClientUtils.prefixLogger(log, stompSessionId);

        return stompSessionId;
    }

    public void subscribe(Map<String,String> subscribeHeaders, Consumer<Object> frameHandler, Consumer<String> errorHandler) {
        if (log.isDebugEnabled()) {
            log.debug("subscribe:" + subscribeHeaders.get(HEADER_DESTINATION));
        }
        final Map<String,String> completeHeaders = completeHeaders(subscribeHeaders);
        stompClient.subscribe(subscribeHeaders,
            (payload) -> {
                if (!done) {
                    if (log.isDebugEnabled()) {
                        log.debug("--> (" + completeHeaders.get(HEADER_DESTINATION) + ") " + ClientUtils.toJsonString(payload));
                    }

                    // TODO !!!!!!!!!
                    // check protocol version
                    /*String protocolVersion = headers.getFirst(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION);
                    if (!WhirlpoolProtocol.PROTOCOL_VERSION.equals(protocolVersion)) {
                        String errorMessage = "Version mismatch: server=" + (protocolVersion != null ? protocolVersion : "unknown") + ", client=" + WhirlpoolProtocol.PROTOCOL_VERSION;
                        errorHandler.accept(errorMessage);
                    }
                    else {
                        frameHandler.accept(payload);
                    }*/

                    // TODO !!!!!!!!!!
                    // unserialize payload
                    /*String messageType = headers.get(WhirlpoolProtocol.HEADER_MESSAGE_TYPE).get(0);
                    try {
                        return Class.forName(messageType);
                    }
                    catch(ClassNotFoundException e) {
                        log.error("unknown message type: " + messageType, e);
                        return null;
                    }*/
                }
                else {
                    log.warn("frame ignored (done) (" + completeHeaders.get(HEADER_DESTINATION) + "): " + ClientUtils.toJsonString(payload));
                }
            }, (error) -> {
                errorHandler.accept((String)error);
            }
        );
    }

    public void disconnect() {
        disconnect(false);
    }

    private void disconnect(boolean connectionLost) {
        this.done = true;

        // don't disconnect session if connectionLost, to avoid forever delays
        if (!connectionLost) {
            try {
                stompClient.disconnect();
            } catch(Exception e) {
                log.error("", e);
            }
        }
        stompClient = null;
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
