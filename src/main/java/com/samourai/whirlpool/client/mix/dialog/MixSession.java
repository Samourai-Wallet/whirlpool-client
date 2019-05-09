package com.samourai.whirlpool.client.mix.dialog;

import com.samourai.stomp.client.IStompTransportListener;
import com.samourai.stomp.client.StompTransport;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.utils.MessageErrorListener;
import com.samourai.whirlpool.client.utils.MessageListener;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.MixMessage;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import java.util.HashMap;
import java.util.Map;
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
  private String logPrefix;

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
    this.transport = null;
    this.logPrefix = null;
    resetDialog();
  }

  private void resetDialog() {
    if (this.dialog != null) {
      this.dialog.stop();
    }
    this.dialog = new MixDialog(listener, this, config);
    listener.onResetMix();
  }

  public synchronized void connect() {
    if (connectBeginTime == null) {
      connectBeginTime = System.currentTimeMillis();
    }

    String wsUrl = WhirlpoolProtocol.getUrlConnect(config.getServer(), config.isSsl());
    if (log.isDebugEnabled()) {
      log.debug("connecting to server: " + wsUrl);
    }

    // connect with a new transport
    Map<String, String> connectHeaders = computeStompHeaders(null);
    transport = new StompTransport(config.getStompClient(), computeTransportListener());
    if (logPrefix != null) {
      transport.setLogPrefix(logPrefix);
    }
    transport.connect(wsUrl, connectHeaders);
  }

  public void setLogPrefix(String logPrefix) {
    dialog.setLogPrefix(logPrefix);
    if (transport != null) {
      transport.setLogPrefix(logPrefix);
    }
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
        new MessageErrorListener<Object, String>() {
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
                  Exception notifiableException = NotifiableException.computeNotifiableException(e);
                  listener.exitOnProtocolError(notifiableException.getMessage());
                }
              } else {
                String notifiableError =
                    "not a SubscribePoolResponse: " + ClientUtils.toJsonString(payload);
                log.error("--> " + privateQueue + ": " + notifiableError);
                listener.exitOnProtocolError(notifiableError);
              }
            } else {
              // 2) input already registered => should be a MixMessage
              MixMessage mixMessage = checkMixMessage(payload);
              if (mixMessage != null) {
                dialog.onPrivateReceived(mixMessage);
              } else {
                String notifiableError = "not a MixMessage: " + ClientUtils.toJsonString(payload);
                log.error("--> " + privateQueue + ": " + notifiableError);
                listener.exitOnProtocolError(notifiableError);
              }
            }
          }

          @Override
          public void onError(String errorMessage) {
            String notifiableException = "subscribe error: " + errorMessage;
            log.error("--> " + privateQueue + ": " + notifiableException);
            listener.exitOnProtocolError(errorMessage); // subscribe error
          }
        },
        new MessageListener<String>() {
          @Override
          public void onMessage(String serverProtocolVersion) {
            // server version mismatch
            listener.exitOnProtocolVersionMismatch(serverProtocolVersion);
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
      String notifiableError =
          "unexpected message from server: " + ClientUtils.toJsonString(payloadClass);
      log.error("Protocol error: " + notifiableError);
      listener.exitOnProtocolError(notifiableError);
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
    }

    return (MixMessage) payload;
  }

  public synchronized void disconnect() {
    if (log.isDebugEnabled()) {
      log.debug("Disconnecting...");
    }
    connectBeginTime = null;
    if (transport != null) {
      transport.disconnect();
    }
    if (log.isDebugEnabled()) {
      log.debug("Disconnected.");
    }
  }

  public void send(String destination, Object message) {
    if (transport != null) {
      transport.send(destination, message);
    } else {
      log.warn("send: ignoring (transport = null)");
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
        if (log.isDebugEnabled()) {
          log.debug("onTransportDisconnected", exception);
        }
        // transport cannot be used
        transport = null;

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
          listener.onConnectionFailWillRetry(config.getReconnectDelay());
          try {
            wait(config.getReconnectDelay() * 1000);
          } catch (Exception e) {
            log.error("", e);
          }
        } else {
          // we just got disconnected
          log.error(" ! connexion lost, reconnecting for a new mix...");
          resetDialog();
          listener.onConnectionLostWillRetry();
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
