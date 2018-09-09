package com.samourai.whirlpool.client.mix.transport;

import com.samourai.whirlpool.protocol.websocket.messages.*;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;

public interface MixDialogListener {

    void onFail();
    void exitOnProtocolError();
    void exitOnResponseError(String notifiableError);
    void exitOnConnectionLost();

    RegisterInputRequest registerInput(RegisterInputMixStatusNotification registerInputMixStatusNotification) throws Exception;
    void postRegisterOutput(RegisterOutputMixStatusNotification registerOutputMixStatusNotification, String registerOutputUrl) throws Exception;
    RevealOutputRequest revealOutput(RevealOutputMixStatusNotification revealOutputMixStatusNotification) throws Exception;
    SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification) throws Exception;

    void onSuccess();

    void onRegisterInputResponse(RegisterInputResponse registerInputResponse);
    void onLiquidityQueuedResponse(LiquidityQueuedResponse liquidityQueuedResponse);

    void onResetMix();
}
