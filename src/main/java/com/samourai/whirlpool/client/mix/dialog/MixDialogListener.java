package com.samourai.whirlpool.client.mix.dialog;

import com.samourai.whirlpool.protocol.websocket.messages.*;
import com.samourai.whirlpool.protocol.websocket.notifications.ConfirmInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;

public interface MixDialogListener {

  void onConnected();

  void onConnectionFailWillRetry(int retryDelay);

  void onConnectionLostWillRetry();

  RegisterInputRequest registerInput(SubscribePoolResponse subscribePoolResponse) throws Exception;

  ConfirmInputRequest confirmInput(
      ConfirmInputMixStatusNotification confirmInputMixStatusNotification) throws Exception;

  void postRegisterOutput(
      RegisterOutputMixStatusNotification registerOutputMixStatusNotification,
      String registerOutputUrl)
      throws Exception;

  RevealOutputRequest revealOutput(
      RevealOutputMixStatusNotification revealOutputMixStatusNotification) throws Exception;

  SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification)
      throws Exception;

  void onConfirmInputResponse(ConfirmInputResponse confirmInputResponse) throws Exception;

  void onMixSuccess();

  void onMixFail();

  void onResetMix();

  void exitOnProtocolError(String notifiableError);

  void exitOnProtocolVersionMismatch(String serverProtocolVersion);

  void exitOnInputRejected(String notifiableError);

  void exitOnDisconnected();
}
