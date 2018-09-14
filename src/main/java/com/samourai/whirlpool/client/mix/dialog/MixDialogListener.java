package com.samourai.whirlpool.client.mix.dialog;

import com.samourai.whirlpool.protocol.websocket.messages.*;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;

public interface MixDialogListener {

    void onConnected();
    RegisterInputRequest registerInput(RegisterInputMixStatusNotification registerInputMixStatusNotification) throws Exception;
    void postRegisterOutput(RegisterOutputMixStatusNotification registerOutputMixStatusNotification, String registerOutputUrl) throws Exception;
    RevealOutputRequest revealOutput(RevealOutputMixStatusNotification revealOutputMixStatusNotification) throws Exception;
    SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification) throws Exception;

    void onRegisterInputResponse(RegisterInputResponse registerInputResponse) throws Exception;
    void onLiquidityQueuedResponse(LiquidityQueuedResponse liquidityQueuedResponse) throws Exception;

    void onSuccess();
    void onFail();
    void onResetMix();

    void exitOnProtocolError();
    void exitOnResponseError(String notifiableError);
    void exitOnConnectionLost();
}
