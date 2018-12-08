package com.samourai.whirlpool.client.mix.dialog;

import com.samourai.stomp.client.IStompTransportListener;
import com.samourai.stomp.client.StompTransport;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.MixMessage;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import java.util.HashMap;
import java.util.Map;
import javax.websocket.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixSession {
  // non-static logger to prefix it with stomp sessionId
  private Logger log = LoggerFactory.getLogger(MixSession.class);

  private MixDialogListener listener;
  private WhirlpoolProtocol whirlpoolProtocol;
  private WhirlpoolClientConfig config;
  private String poolId;
  private StompTransport transport;

  // connect data
  private Long connectBeginTime;

  // session data
  private MixDialog dialog;
  private SubscribePoolResponse subscribePoolResponse;

  public MixSession(
      MixDialogListener listener,
      WhirlpoolProtocol whirlpoolProtocol,
      WhirlpoolClientConfig config,
      String poolId) {
    this.listener = listener;
    this.whirlpoolProtocol = whirlpoolProtocol;
    this.config = config;
    this.poolId = poolId;
    this.transport = new StompTransport(config.getStompClient(), computeTransportListener());
    resetDialog();
  }

  private void resetDialog() {
    this.dialog = new MixDialog(listener, transport, config);
  }

  public synchronized void connect() {
    if (connectBeginTime == null) {
      connectBeginTime = System.currentTimeMillis();
    }

    String wsUrl = WhirlpoolProtocol.getUrlConnect(config.getServer(), config.isSsl());
    if (log.isDebugEnabled()) {
      log.debug("connecting to server: " + wsUrl);
    }

    // connect
    Map<String, String> connectHeaders = computeStompHeaders(null);
    transport.connect(wsUrl, connectHeaders);
  }

  public void setLogPrefix(String logPrefix) {
    dialog.setLogPrefix(logPrefix);
    transport.setLogPrefix(logPrefix);
    log = ClientUtils.prefixLogger(log, logPrefix);
  }

  private void subscribe() {
    // reset session
    subscribePoolResponse = null;

    // subscribe to private queue
    final String privateQueue =
        whirlpoolProtocol.WS_PREFIX_USER_PRIVATE + whirlpoolProtocol.WS_PREFIX_USER_REPLY;
    transport.subscribe(
        computeStompHeaders(privateQueue),
        new MessageHandler.Whole<Object>() {
          @Override
          public void onMessage(Object payload) {
            if (subscribePoolResponse == null) {
              if (SubscribePoolResponse.class.isAssignableFrom(payload.getClass())) {
                // 1) input not registered yet => should be a SubscribePoolResponse
                subscribePoolResponse = (SubscribePoolResponse) payload;

                // REGISTER_INPUT
                try {
                  registerInput(subscribePoolResponse);
                } catch (Exception e) {
                  log.error("Unable to register input", e);
                  listener.exitOnProtocolError();
                }
              } else {
                log.error(
                    "--> "
                        + privateQueue
                        + ": not a SubscribePoolResponse: "
                        + ClientUtils.toJsonString(payload));
                listener.exitOnProtocolError();
              }
            } else {
              // 2) input already registered => should be a MixMessage
              MixMessage mixMessage = checkMixMessage(payload);
              if (mixMessage != null) {
                dialog.onPrivateReceived(mixMessage);
              } else {
                log.error(
                    "--> "
                        + privateQueue
                        + ": not a MixMessage: "
                        + ClientUtils.toJsonString(payload));
                listener.exitOnProtocolError();
              }
            }
          }
        },
        new MessageHandler.Whole<String>() {
          @Override
          public void onMessage(String errorMessage) {
            log.error("--> " + privateQueue + ": subscribe error: " + errorMessage);
            listener.exitOnResponseError(errorMessage); // probably a version mismatch
          }
        });

    // will automatically receive mixStatus in response of subscription
    if (log.isDebugEnabled()) {
      log.debug("subscribed to server");
    }
  }

  private void registerInput(SubscribePoolResponse subscribePoolResponse) throws Exception {
    RegisterInputRequest registerInputRequest = listener.registerInput(subscribePoolResponse);
    transport.send(WhirlpoolEndpoint.WS_REGISTER_INPUT, registerInputRequest);
  }

  private MixMessage checkMixMessage(Object payload) {
    // should be MixMessage
    Class payloadClass = payload.getClass();
    if (!MixMessage.class.isAssignableFrom(payloadClass)) {
      log.error(
          "Protocol error: unexpected message from server: "
              + ClientUtils.toJsonString(payloadClass));
      listener.exitOnProtocolError();
      return null;
    }

    MixMessage mixMessage = (MixMessage) payload;

    // reset dialog on new mixId
    if (mixMessage.mixId != null
        && dialog.getMixId() != null
        && !dialog
            .getMixId()
            .equals(mixMessage.mixId)) { // mixMessage.mixId is null for ErrorResponse
      if (log.isDebugEnabled()) {
        log.debug("new mixId detected: " + mixMessage.mixId);
      }
      resetDialog();
      listener.onResetMix();
    }

    return (MixMessage) payload;
  }

  public synchronized void disconnect() {
    if (log.isDebugEnabled()) {
      log.debug("Disconnecting...");
    }
    connectBeginTime = null;
    transport.disconnect();
    if (log.isDebugEnabled()) {
      log.debug("Disconnected.");
    }
  }

  //

  private Map<String, String> computeStompHeaders(String destination) {
    Map<String, String> stompHeaders = new HashMap<String, String>();
    stompHeaders.put(WhirlpoolProtocol.HEADER_POOL_ID, poolId);
    if (destination != null) {
      stompHeaders.put(StompTransport.HEADER_DESTINATION, destination);
    }
    return stompHeaders;
  }

  private IStompTransportListener computeTransportListener() {
    return new IStompTransportListener() {

      @Override
      public synchronized void onTransportConnected(String stompUsername) {
        if (log.isDebugEnabled()) {
          long elapsedTime = (System.currentTimeMillis() - connectBeginTime) / 1000;
          log.debug("Connected in " + elapsedTime + "s");
        }
        connectBeginTime = null;

        if (stompUsername == null) {
          // no way to get stompUsername on Android?
          stompUsername = "android";
        }
        setLogPrefix(stompUsername);
        if (log.isDebugEnabled()) {
          log.debug("connected to server, stompUsername=" + stompUsername);
        }

        // will get SubscribePoolResponse and start dialog
        subscribe();

        listener.onConnected();
      }

      @Override
      public synchronized void onTransportDisconnected(Throwable exception) {

        if (connectBeginTime != null) {
          // we were trying connect
          long elapsedTime = System.currentTimeMillis() - connectBeginTime;
          if (elapsedTime > config.getReconnectUntil() * 1000) {
            // retry time exceeded, aborting
            log.info(
                " ! Failed to connect to server. Please check your connectivity or retry later.");
            connectBeginTime = null;
            listener.exitOnDisconnected();
            return;
          }

          // wait delay before retrying
          log.info(" ! connexion failed, retrying in " + config.getReconnectDelay() + "s");
          try {
            wait(config.getReconnectDelay() * 1000);
          } catch (Exception e) {
            log.error("", e);
          }
        } else {
          // we just got disconnected
          if (dialog.gotConfirmInputResponse()) {
            log.error(" ! connexion lost, reconnecting for resuming joined mix...");
            // keep current dialog
          } else {
            log.error(" ! connexion lost, reconnecting for a new mix...");
            dialog = null;
            listener.onResetMix();
          }
        }

        // reconnect
        connect();
      }
    };
  }

  //
  protected StompTransport __getTransport() {
    return transport;
  }
}
