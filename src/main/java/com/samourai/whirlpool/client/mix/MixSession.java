package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.mix.transport.MixDialogListener;
import com.samourai.whirlpool.client.mix.transport.StompTransport;
import com.samourai.whirlpool.client.mix.transport.TransportListener;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.WhirlpoolMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

public class MixSession {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private MixDialogListener listener;
    private WhirlpoolProtocol whirlpoolProtocol;
    private WhirlpoolClientConfig config;
    private String poolId;
    private StompTransport transport;

    // connect data
    private boolean connecting;
    private String stompSessionId;

    // session data
    private MixDialog dialog;

    public MixSession(MixDialogListener listener, WhirlpoolProtocol whirlpoolProtocol, WhirlpoolClientConfig config, String poolId) {
        this.listener = listener;
        this.whirlpoolProtocol = whirlpoolProtocol;
        this.config = config;
        this.poolId = poolId;
        this.transport = new StompTransport(computeTransportListener());
        resetDialog();
    }

    private void resetDialog() {
        this.dialog = new MixDialog(listener, transport, config);
    }

    public void connect() throws Exception {
        reconnect(); // throws exception on failure
    }

    private void connectOrException() throws Exception {
        String wsUrl ="ws://" + config.getServer() + WhirlpoolProtocol.ENDPOINT_CONNECT;
        if (log.isDebugEnabled()) {
            log.debug("connecting to server: " + wsUrl);
        }

        // connect
        StompHeaders connectHeaders = computeStompHeaders(null);
        stompSessionId = transport.connect(wsUrl, connectHeaders);
        setLogPrefix(stompSessionId);
        if (log.isDebugEnabled()) {
            log.debug("connected to server, stompSessionId=" + stompSessionId);
        }

        // start dialog with server
        subscribe();
    }

    private void setLogPrefix(String logPrefix) {
        dialog.setLogPrefix(logPrefix);
        log = ClientUtils.prefixLogger(log, logPrefix);
    }

    private void reconnect() throws Exception {
        connecting = true;
        long beginTime = System.currentTimeMillis();
        long elapsedTime;
        do {
            try {
                connectOrException();

                // success
                connecting = false;
                return;
            }
            catch(Exception e) {
            }
            log.info(" ! Reconnection failed, retrying in "+config.getReconnectDelay()+"s");

            // wait delay before retrying
            synchronized (this) {
                try {
                    wait(config.getReconnectDelay() * 1000);
                }
                catch(Exception e) {
                    log.error("", e);
                }
            }
            elapsedTime = System.currentTimeMillis() - beginTime;
        }
        while(elapsedTime < config.getReconnectUntil() * 1000);

        // aborting
        connecting = false;
        throw new Exception("Reconnecting failed");
    }

    private void subscribe() {
        // subscribe to private responses first (to receive error responses)
        String privateQueue = whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY;
        transport.subscribe(computeStompHeaders(privateQueue),
                (payload) -> {
                    Optional<WhirlpoolMessage> whirlpoolMessage = checkMessage(payload);
                    if (whirlpoolMessage.isPresent()) {
                        dialog.onPrivateReceived(whirlpoolMessage.get());
                    } else {
                        log.error("--> " + privateQueue + ": not a WhirlpoolMessage: " + ClientUtils.toJsonString(payload));
                        listener.exitOnProtocolError();
                    }
                },
                (errorMessage) -> {
                    log.error("--> " + privateQueue + ": subscribe error: " + errorMessage);
                    listener.exitOnResponseError(errorMessage); // probably a version mismatch
                    listener.exitOnProtocolError();
                }
        );

        // subscribe mixStatusNotifications
        transport.subscribe(computeStompHeaders(whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE),
                (payload) -> {
                    Optional<WhirlpoolMessage> whirlpoolMessage = checkMessage(payload);
                    if (whirlpoolMessage.isPresent()) {
                        dialog.onBroadcastReceived(whirlpoolMessage.get());
                    } else {
                        log.error("--> " + whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE + ": not a WhirlpoolMessage: " + ClientUtils.toJsonString(payload));
                        listener.exitOnProtocolError();
                    }
                },
                (errorMessage) -> {
                    log.error("--> " + whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE + ": subscribe error: " + errorMessage);
                    listener.exitOnResponseError(errorMessage); // probably a version mismatch
                    listener.exitOnProtocolError();
                }
        );

        // will automatically receive mixStatus in response of subscription
        if (log.isDebugEnabled()) {
            log.debug("subscribed to server");
        }
    }

    private Optional<WhirlpoolMessage> checkMessage(Object payload) {
        // should be WhirlpoolMessage
        Class payloadClass = payload.getClass();
        if (!WhirlpoolMessage.class.isAssignableFrom(payloadClass)) {
            log.error("Protocol error: unexpected message from server: " + ClientUtils.toJsonString(payloadClass));
            listener.exitOnProtocolError();
            return Optional.empty();
        }

        WhirlpoolMessage whirlpoolMessage = (WhirlpoolMessage)payload;

        // reset dialog on new mixId
        if (whirlpoolMessage.mixId != null && dialog.getMixId() != null && !dialog.getMixId().equals(whirlpoolMessage.mixId)) { // whirlpoolMessage.mixId is null for ErrorResponse
            if (log.isDebugEnabled()) {
                log.debug("new mixId detected: " + whirlpoolMessage.mixId);
            }
            resetDialog();
            listener.onResetMix();
        }

        return Optional.of((WhirlpoolMessage) payload);
    }

    public void disconnect() {
        if (log.isDebugEnabled()) {
            log.debug("Disconnecting...");
        }
        stompSessionId = null;
        connecting = false;
        transport.disconnect();
        if (log.isDebugEnabled()) {
            log.debug("Disconnected.");
        }
    }

    //

    private StompHeaders computeStompHeaders(String destination) {
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.set(WhirlpoolProtocol.HEADER_POOL_ID, poolId);
        if (destination != null) {
            stompHeaders.setDestination(destination);
        }
        return stompHeaders;
    }

    private TransportListener computeTransportListener() {
        return new TransportListener() {
            @Override
            public void onTransportConnectionLost(Throwable exception) {
                // ignore connectionLost when connecting (already managed)
                if (!connecting) {
                    if (dialog.gotRegisterInputResponse()) {
                        log.error(" ! connection lost, reconnecting for resuming joined mix...");
                        // keep current dialog
                    } else {
                        log.error(" ! connection lost, reconnecting for a new mix...");
                        resetDialog();
                        listener.onResetMix();
                    }

                    try {
                        reconnect();
                    }
                    catch(Exception e) {
                        log.info(" ! Failed to connect to server. Please check your connectivity or retry later.");
                        listener.exitOnConnectionLost();
                    }
                }
            }
        };
    }

    //

    protected StompTransport __getTransport() {
        return transport;
    }
}
