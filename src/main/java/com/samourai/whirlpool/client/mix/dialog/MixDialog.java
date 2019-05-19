package com.samourai.whirlpool.client.mix.dialog;

import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolEndpoint;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.websocket.MixMessage;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputResponse;
import com.samourai.whirlpool.protocol.websocket.messages.ErrorResponse;
import com.samourai.whirlpool.protocol.websocket.messages.RevealOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.SigningRequest;
import com.samourai.whirlpool.protocol.websocket.notifications.ConfirmInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.protocol.websocket.notifications.MixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixDialog {
  // non-static logger to prefix it with stomp sessionId
  private Logger log = LoggerFactory.getLogger(MixDialog.class);

  private MixDialogListener listener;
  private MixSession mixSession;
  private WhirlpoolClientConfig clientConfig;

  // mix data
  private String mixId;
  private MixStatus mixStatus;
  private boolean gotConfirmInputResponse; // will get it after CONFIRM_INPUT
  private RegisterOutputMixStatusNotification
      earlyRegisterOutputMixStatusNotification; // we may get early REGISTER_OUTPUT notification
  // (before registerInputResponse)

  // computed values
  private Set<MixStatus> mixStatusCompleted = new HashSet<MixStatus>();
  private boolean done;

  public MixDialog(
      MixDialogListener listener, MixSession mixSession, WhirlpoolClientConfig clientConfig) {
    this.listener = listener;
    this.clientConfig = clientConfig;
    this.mixSession = mixSession;
  }

  public void setLogPrefix(String logPrefix) {
    this.log = ClientUtils.prefixLogger(log, logPrefix);
  }

  public synchronized void onPrivateReceived(MixMessage mixMessage) {
    if (done) {
      log.info("Ignoring mixMessage (done): " + ClientUtils.toJsonString(mixMessage));
      return;
    }
    try {
      Class payloadClass = mixMessage.getClass();
      if (ErrorResponse.class.isAssignableFrom(payloadClass)) {
        String errorMessage = ((ErrorResponse) mixMessage).message;
        exitOnResponseError(errorMessage);
      } else {
        if (mixId == null) {
          // track mixId as soon as we joined a mix (either ConfirmInputResponse or early
          // RegisterOutputMixStatusNotification) but not ConfirmInputMixStatusNotification which
          // doesn't garantee to join the mix
          if (!ConfirmInputMixStatusNotification.class.isAssignableFrom(payloadClass)) {
            mixId = mixMessage.mixId;
            if (log.isDebugEnabled()) {
              log.debug("mixId=" + mixId);
            }
          }
        } else if (!mixMessage.mixId.equals(mixId)) {
          log.error("Invalid mixId: expected=" + mixId + ", actual=" + mixMessage.mixId);
          throw new Exception("Invalid mixId");
        }

        if (MixStatusNotification.class.isAssignableFrom(mixMessage.getClass())) {
          onMixStatusNotificationChange((MixStatusNotification) mixMessage);
        } else if (ConfirmInputResponse.class.isAssignableFrom(payloadClass)) {
          this.gotConfirmInputResponse = true;
          listener.onConfirmInputResponse((ConfirmInputResponse) mixMessage);

          // if we received early REGISTER_OUTPUT notification, register ouput now
          if (earlyRegisterOutputMixStatusNotification != null) {
            doRegisterOutput(earlyRegisterOutputMixStatusNotification);
          }
        } else {
          log.error(
              "Unexpected mixMessage, registeredInput=true: "
                  + ClientUtils.toJsonString(mixMessage));
        }
      }
    } catch (NotifiableException e) {
      log.error("onPrivateReceived NotifiableException: " + e.getMessage());
      exitOnResponseError(e.getMessage());
    } catch (Exception e) {
      log.error("onPrivateReceived Exception", e);
      exitOnPrivateReceivedException(e);
    }
  }

  private void exitOnPrivateReceivedException(Exception e) {
    log.error("Protocol error", e);
    String notifiableError = "onPrivate: " + e.getClass().getName();
    listener.exitOnProtocolError(notifiableError);
    done = true;
  }

  private void exitOnResponseError(String error) {
    listener.exitOnInputRejected(error);
    done = true;
  }

  public void stop() {
    this.done = true;
  }

  private void onMixStatusNotificationChange(MixStatusNotification notification) throws Exception {

    // ignore duplicate CONFIRM_INPUT: we may try to confirm for several mixes before joining
    if (!MixStatus.CONFIRM_INPUT.equals(notification.status)) {
      // check status chronology
      if (mixStatusCompleted.contains(notification.status)) {
        throw new Exception("mixStatus already completed: " + notification.status);
      }
      if (mixStatus != null && notification.status.equals(mixStatus)) {
        throw new Exception("Duplicate mixStatusNotification: " + mixStatus);
      }
    }
    this.mixStatus = notification.status;

    if (MixStatus.FAIL.equals(notification.status)) {
      done = true;
      listener.onMixFail();
      return;
    }

    if (MixStatus.CONFIRM_INPUT.equals(notification.status)) {
      ConfirmInputMixStatusNotification confirmInputMixStatusNotification =
          (ConfirmInputMixStatusNotification) notification;

      ConfirmInputRequest confirmInputRequest =
          listener.confirmInput(confirmInputMixStatusNotification);
      mixSession.send(WhirlpoolEndpoint.WS_CONFIRM_INPUT, confirmInputRequest);
      mixStatusCompleted.add(MixStatus.CONFIRM_INPUT);
    } else if (mixStatusCompleted.contains(MixStatus.CONFIRM_INPUT)) {
      if (gotConfirmInputResponse()) {

        if (MixStatus.REGISTER_OUTPUT.equals(notification.status)) {
          doRegisterOutput((RegisterOutputMixStatusNotification) notification);
          mixStatusCompleted.add(MixStatus.REGISTER_OUTPUT);

        } else if (mixStatusCompleted.contains(MixStatus.REGISTER_OUTPUT)) {

          // don't reveal output if already signed
          if (!mixStatusCompleted.contains(MixStatus.SIGNING)
              && MixStatus.REVEAL_OUTPUT.equals(notification.status)) {
            RevealOutputRequest revealOutputRequest =
                listener.revealOutput((RevealOutputMixStatusNotification) notification);
            mixSession.send(WhirlpoolEndpoint.WS_REVEAL_OUTPUT, revealOutputRequest);
            mixStatusCompleted.add(MixStatus.REVEAL_OUTPUT);

          } else if (!mixStatusCompleted.contains(
              MixStatus.REVEAL_OUTPUT)) { // don't sign or success if output was revealed

            if (MixStatus.SIGNING.equals(notification.status)) {
              SigningRequest signingRequest =
                  listener.signing((SigningMixStatusNotification) notification);
              mixSession.send(WhirlpoolEndpoint.WS_SIGNING, signingRequest);
              mixStatusCompleted.add(MixStatus.SIGNING);

            } else if (mixStatusCompleted.contains(MixStatus.SIGNING)) {

              if (MixStatus.SUCCESS.equals(notification.status)) {
                listener.onMixSuccess();
                done = true;
                return;
              }
            } else {
              log.warn(" x SIGNING not completed");
              if (log.isDebugEnabled()) {
                log.error(
                    "Ignoring mixStatusNotification: " + ClientUtils.toJsonString(notification));
              }
            }
          } else {
            log.warn(" x REVEAL_OUTPUT already completed");
            if (log.isDebugEnabled()) {
              log.error(
                  "Ignoring mixStatusNotification: " + ClientUtils.toJsonString(notification));
            }
          }
        } else {
          log.warn(" x REGISTER_OUTPUT not completed");
          if (log.isDebugEnabled()) {
            log.error("Ignoring mixStatusNotification: " + ClientUtils.toJsonString(notification));
          }
        }
      } else {
        if (MixStatus.REGISTER_OUTPUT.equals(notification.status)) {
          // early REGISTER_OUTPUT notification (before RegisterInputResponse).
          // keep it to register output as soon as we receive RegisterInputResponse
          this.earlyRegisterOutputMixStatusNotification =
              (RegisterOutputMixStatusNotification) notification;
        }

        log.info(" > Trying to join current mix...");
      }
    } else {
      log.info(" > Waiting for next mix...");
      if (log.isDebugEnabled()) {
        log.debug("Current mix status: " + notification.status);
      }
    }
  }

  private void doRegisterOutput(
      RegisterOutputMixStatusNotification registerOutputMixStatusNotification) throws Exception {
    try {
      String registerOutputUrl =
          WhirlpoolProtocol.getUrlRegisterOutput(clientConfig.getServer(), clientConfig.isSsl());
      listener.postRegisterOutput(registerOutputMixStatusNotification, registerOutputUrl);
    } catch (HttpException e) {
      String restErrorResponseMessage = ClientUtils.parseRestErrorMessage(e);
      if (restErrorResponseMessage != null) {
        throw new NotifiableException(restErrorResponseMessage);
      }
      throw e;
    }
  }

  protected boolean gotConfirmInputResponse() {
    return gotConfirmInputResponse;
  }

  public String getMixId() {
    return mixId;
  }
}
