package com.samourai.stomp.client;

import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.utils.MessageErrorListener;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** STOMP communication. */
public class StompTransport {
  // non-static logger to prefix it with stomp sessionId
  private Logger log = LoggerFactory.getLogger(StompTransport.class);
  private static final String HEADER_USERNAME = "user-name";
  public static final String HEADER_DESTINATION = "destination";

  private IStompClient stompClient;
  private IStompTransportListener listener;

  private boolean done;

  public StompTransport(IStompClient stompClient, IStompTransportListener listener) {
    this.stompClient = stompClient;
    this.listener = listener;
  }

  public String connect(String wsUrl, Map<String, String> connectHeaders) {
    done = false;
    stompClient.connect(
        wsUrl,
        connectHeaders,
        new MessageErrorListener<IStompMessage, Throwable>() {
          // onConnect
          @Override
          public void onMessage(IStompMessage connectedHeaders) {
            if (!done) {
              String stompUsername = null;
              if (connectedHeaders != null) { // no way to get connectedHeaders on Android?
                stompUsername = connectedHeaders.getStompHeader(HEADER_USERNAME);
              }
              if (log.isDebugEnabled()) {
                log.debug("stompUsername=" + (stompUsername != null ? stompUsername : "null"));
              }
              listener.onTransportConnected(stompUsername);
            } else {
              log.info("IStompClient.onConnect: message ignored (done=true)");
            }
          }

          // onDisconnect
          @Override
          public void onError(Throwable exception) {
            if (!done) {
              disconnect();
              listener.onTransportDisconnected(exception);
            } else {
              log.info("IStompClient.onDisconnect: message ignored (done=true)");
            }
          }
        });

    String stompSessionId = stompClient.getSessionId();
    return stompSessionId;
  }

  public void subscribe(
      Map<String, String> subscribeHeaders, final MessageErrorListener<Object, String> listener) {
    if (log.isDebugEnabled()) {
      log.debug("subscribe:" + subscribeHeaders.get(HEADER_DESTINATION));
    }
    final Map<String, String> completeHeaders = completeHeaders(subscribeHeaders);

    MessageErrorListener<IStompMessage, String> onMessageOnErrorListener =
        new MessageErrorListener<IStompMessage, String>() {
          @Override
          public void onMessage(IStompMessage stompMessage) {
            Object payload = stompMessage.getPayload();
            if (!done) {
              if (log.isDebugEnabled()) {
                log.debug(
                    "--> ("
                        + completeHeaders.get(HEADER_DESTINATION)
                        + ") "
                        + ClientUtils.toJsonString(payload));
              }

              // check protocol version
              String protocolVersion =
                  stompMessage.getStompHeader(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION);
              if (protocolVersion == null
                  || !WhirlpoolProtocol.PROTOCOL_VERSION.equals(protocolVersion)) {
                String errorMessage =
                    "Version mismatch: server="
                        + (protocolVersion != null ? protocolVersion : "unknown")
                        + ", client="
                        + WhirlpoolProtocol.PROTOCOL_VERSION;
                listener.onError(errorMessage);
                return;
              }
              listener.onMessage(payload);
            } else {
              log.warn(
                  "frame ignored (done) ("
                      + completeHeaders.get(HEADER_DESTINATION)
                      + "): "
                      + ClientUtils.toJsonString(payload));
            }
          }

          @Override
          public void onError(String error) {
            listener.onError(error);
          }
        };

    stompClient.subscribe(subscribeHeaders, onMessageOnErrorListener);
  }

  public void disconnect() {
    if (log.isDebugEnabled()) {
      log.debug("disconnect");
    }
    this.done = true;
    try {
      stompClient.disconnect();
    } catch (Exception e) {
    }
  }

  // STOMP communication

  public void send(String destination, Object message) {
    Map<String, String> stompHeaders = new HashMap<String, String>();
    stompHeaders.put(HEADER_DESTINATION, destination);
    stompHeaders = completeHeaders(stompHeaders);
    if (log.isDebugEnabled()) {
      log.debug("send: " + destination + ":" + ClientUtils.toJsonString(message));
    }
    stompClient.send(stompHeaders, message);
  }

  private Map<String, String> completeHeaders(Map<String, String> stompHeaders) {
    stompHeaders.put(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
    return stompHeaders;
  }

  public void setLogPrefix(String logPrefix) {
    this.log = ClientUtils.prefixLogger(log, logPrefix);
  }
}
