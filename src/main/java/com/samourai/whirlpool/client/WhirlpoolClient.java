package com.samourai.whirlpool.client;

import ch.qos.logback.classic.Level;
import com.samourai.whirlpool.client.beans.RoundResultSuccess;
import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.utils.ClientFrameHandler;
import com.samourai.whirlpool.client.utils.ClientSessionHandler;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.utils.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.v1.messages.*;
import com.samourai.whirlpool.protocol.v1.notifications.*;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public class WhirlpoolClient {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // server settings
    private WhirlpoolClientConfig config;

    // round settings
    private RoundParams roundParams;
    private WhirlpoolClientListener listener;

    // round data
    private RoundStatusNotification roundStatusNotification;
    private ErrorResponse errorResponse;
    private RSAKeyParameters serverPublicKey;
    private long denomination;
    private long minerFee;
    private byte[] signedBordereau; // will get it after REGISTER_INPUT
    private PeersPaymentCodesResponse peersPaymentCodesResponse; // will get it after REGISTER_INPUT

    // computed values
    private String bordereau; // will generate it randomly
    private RSABlindingParameters blindingParams;
    private String receiveAddress;
    private String receiveUtxoHash;
    private Integer receiveUtxoIdx;
    private Map<RoundStatus,Boolean> roundStatusCompleted = new HashMap<>();

    private ClientCryptoService clientCryptoService;
    private WhirlpoolProtocol whirlpoolProtocol;
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private String stompUsername;
    private String logPrefix;
    private boolean reconnecting;
    private boolean resuming;
    private boolean done;

    public WhirlpoolClient(WhirlpoolClientConfig config) {
        this(config, new ClientCryptoService(), new WhirlpoolProtocol());
    }

    public WhirlpoolClient(WhirlpoolClientConfig config, ClientCryptoService clientCryptoService, WhirlpoolProtocol whirlpoolProtocol) {
        this.config = config;
        this.clientCryptoService = clientCryptoService;
        this.whirlpoolProtocol = whirlpoolProtocol;
    }

    public void whirlpool(RoundParams roundParams, WhirlpoolClientListener listener) {
        this.roundParams = roundParams;
        this.listener = listener;

        try {
            connectAndJoin();
        }
        catch (Exception e) {
            reconnectOrExit();
        }
    }

    private void connectAndJoin() throws Exception {
        connect();
        subscribe();
    }

    private void connect() throws Exception {
        try {
            if (this.stompClient != null) {
                log.warn("connect() : already connected");
                return;
            }

            log.info(" • connecting to " + config.getWsUrl());
            stompClient = createWebSocketClient();
            stompSession = stompClient.connect(config.getWsUrl(), new ClientSessionHandler(this)).get();

            // prefix logger
            Level level = ((ch.qos.logback.classic.Logger)log).getEffectiveLevel();
            String loggerName = logPrefix != null ? logPrefix : stompSession.getSessionId();
            log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()+"("+loggerName+")");
            ((ch.qos.logback.classic.Logger)log).setLevel(level);
            log.info(" • connected");
            if (log.isDebugEnabled()) {
                log.debug("stompSessionId=" + stompSession.getSessionId() + ", stompUsername=" + stompUsername);
            }

        }
        catch(Exception e) {
            log.error(" ! connection failed: "+e.getMessage());
            stompClient = null;
            stompSession = null;
            stompUsername = null;
            throw e;
        }
    }

    private void disconnect() {
        log.info(" • disconnecting...");
        disconnect(false);
    }

    private void disconnect(boolean connectionLost) {
        if (stompSession != null) {
            // don't disconnect session if connectionLost, to avoid forever delays
            if (!connectionLost) {
                stompSession.disconnect();
            }
            stompSession = null;
            stompUsername = null;
        }
        if (stompClient != null) {
            stompClient.stop();
            stompClient = null;
        }
        log.info(" • disconnected");
    }

    private void subscribe() {
        stompSession.subscribe(whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE,
            new ClientFrameHandler(whirlpoolProtocol, (payload) -> {
                if (!done) {
                    if (log.isDebugEnabled()) {
                        log.debug("--> (" + whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE + ") " + ClientUtils.toJsonString(payload));
                    }
                    onBroadcastReceived(payload);
                }
                else {
                    log.warn("--> (" + whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE + ") ignored (done): " + ClientUtils.toJsonString(payload));
                }
            })
        );
        stompSession.subscribe(whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY,
            new ClientFrameHandler(whirlpoolProtocol, (payload) -> {
                if (!done) {
                    if (log.isDebugEnabled()) {
                        log.debug("--> (" + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY + ") " + ClientUtils.toJsonString(payload));
                    }
                    onPrivateReceived(payload);
                }
                else {
                    log.warn("--> (" + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY + ") ignored (done): " + ClientUtils.toJsonString(payload));
                }
            })
        );
        // will automatically receive roundStatus in response of subscription
        if (log.isDebugEnabled()) {
            log.debug(" • subscribed to server");
        }
    }

    private void onBroadcastReceived(Object payload) {
        if (log.isDebugEnabled()) {
            log.info("--> (broadcast) " + ClientUtils.toJsonString(payload));
        }
        if (RoundStatusNotification.class.isAssignableFrom(payload.getClass())) {
            onRoundStatusNotificationChange((RoundStatusNotification)payload);
        }
    }

    private synchronized void onRoundStatusNotificationChange(RoundStatusNotification notification) {
        try {
            if (this.roundStatusNotification != null && !this.roundStatusNotification.roundId.equals(notification.roundId)) {
                // roundId changed, reset...
                if (resuming) {
                    log.error(" ! Unable to resume joined round: new round detected");
                    resuming = false;
                } else {
                    log.info("new round detected: " + notification.roundId);
                }
                this.resetRound();
            }
            if (this.roundStatusNotification == null || !notification.status.equals(this.roundStatusNotification.status)) {
                this.roundStatusNotification = notification;
                if (!roundStatusCompleted.containsKey(notification.status)) {
                    switch (notification.status) {
                        case REGISTER_INPUT:
                            registerInput((RegisterInputRoundStatusNotification) roundStatusNotification);
                            break;
                        case REGISTER_OUTPUT:
                            registerOutputIfReady((RegisterOutputRoundStatusNotification) roundStatusNotification);
                            break;
                        case REVEAL_OUTPUT_OR_BLAME:
                            revealOutput();
                            break;
                        case SIGNING:
                            this.signing((SigningRoundStatusNotification) roundStatusNotification);
                            break;
                        case SUCCESS:
                            logStep(4, "SUCCESS");
                            log.info("Funds will be received at " + this.receiveAddress + ", utxo " + this.receiveUtxoHash + ":" + this.receiveUtxoIdx);

                            RoundResultSuccess roundResultSuccess = new RoundResultSuccess(this.receiveAddress, this.receiveUtxoHash, this.receiveUtxoIdx);
                            this.listener.success(roundResultSuccess);
                            exit();
                            break;
                        case FAIL:
                            logStep(4, "FAILURE");
                            failAndExit();
                            break;
                    }
                }
            }
        }
        catch(Exception e) {
            log.error("", e);
            failAndExit();
        }
    }

    private void registerOutputIfReady(RegisterOutputRoundStatusNotification registerOutputRoundStatusNotification) {
        if (this.signedBordereau != null && this.peersPaymentCodesResponse != null) {
            try {
                this.registerOutput(registerOutputRoundStatusNotification);
            } catch (Exception e) {
                log.error("registerOutput failed", e);
            }
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("Cannot register output yet, no signedBordereau or peersPaymentCodesResponse received yet");
            }
        }
    }

    private synchronized void onPrivateReceived(Object payload) {
        Class payloadClass = payload.getClass();
        if (ErrorResponse.class.isAssignableFrom(payloadClass)) {
            onErrorResponse((ErrorResponse)payload);
        }
        else if (RoundStatusNotification.class.isAssignableFrom(payload.getClass())) {
            onRoundStatusNotificationChange((RoundStatusNotification)payload);
        }
        else if (RegisterInputResponse.class.isAssignableFrom(payloadClass)) {
            onRegisterInputResponse((RegisterInputResponse)payload);
        }
        else if (PeersPaymentCodesResponse.class.isAssignableFrom(payloadClass)) {
            onPeersPaymentCodeResponse((PeersPaymentCodesResponse)payload);
        }
    }

    private void onErrorResponse(ErrorResponse errorResponse) {
        this.errorResponse = errorResponse;
        logStep(4, "ERROR");
        log.error(errorResponse.message);
        failAndExit();
    }

    private void failAndExit() {
        this.listener.fail();
        exit();
    }

    private void onRegisterInputResponse(RegisterInputResponse payload) {
        log.info(" > Joined round " + this.roundStatusNotification.roundId);
        this.signedBordereau = payload.signedBordereau;
        if (RoundStatus.REGISTER_OUTPUT.equals(this.roundStatusNotification.status)) {
            registerOutputIfReady((RegisterOutputRoundStatusNotification)this.roundStatusNotification);
        }
    }

    private boolean hasRegisteredInput() {
        return (this.signedBordereau != null);
    }

    private void onPeersPaymentCodeResponse(PeersPaymentCodesResponse payload) {
        this.peersPaymentCodesResponse = payload;
        if (RoundStatus.REGISTER_OUTPUT.equals(this.roundStatusNotification.status)) {
            registerOutputIfReady((RegisterOutputRoundStatusNotification)this.roundStatusNotification);
        }
    }

    private WebSocketStompClient createWebSocketClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    public RoundParams computeNextRoundParams() {
        ISimpleWhirlpoolClient nextSimpleWhirlpoolClient = roundParams.getSimpleWhirlpoolClient().computeSimpleWhirlpoolClientForNextRound();
        return new RoundParams(this.receiveUtxoHash, this.receiveUtxoIdx, roundParams.getPaymentCode(), nextSimpleWhirlpoolClient, true);
    }

    private void resetRound() {
        if (log.isDebugEnabled()) {
            log.debug("resetRound");
        }
        // round data
        this.roundStatusNotification = null;
        this.errorResponse = null;
        this.serverPublicKey = null;
        this.denomination = -1;
        this.minerFee = -1;
        this.signedBordereau = null;
        this.peersPaymentCodesResponse = null;

        // computed values
        this.bordereau = null;
        this.blindingParams = null;
        this.receiveAddress = null;
        this.receiveUtxoHash = null;
        this.receiveUtxoIdx = null;
        this.roundStatusCompleted = new HashMap<>();
        this.resuming = false;
    }

    private void registerInput(RegisterInputRoundStatusNotification registerInputRoundStatusNotification) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();

        logStep(1, "REGISTER_INPUT");

        // get round settings
        this.serverPublicKey = ClientUtils.publicKeyUnserialize(registerInputRoundStatusNotification.getPublicKey());
        String serverNetworkId = registerInputRoundStatusNotification.getNetworkId();
        if (!networkParameters.getPaymentProtocolId().equals(serverNetworkId)) {
            throw new Exception("Client/server networkId mismatch: server is runinng "+serverNetworkId+", client is expecting "+networkParameters.getPaymentProtocolId());
        }
        this.denomination = registerInputRoundStatusNotification.getDenomination();
        this.minerFee = registerInputRoundStatusNotification.getMinerFee();

        ISimpleWhirlpoolClient simpleWhirlpoolClient = roundParams.getSimpleWhirlpoolClient();

        RegisterInputRequest registerInputRequest = new RegisterInputRequest();
        registerInputRequest.utxoHash = roundParams.getUtxoHash();
        registerInputRequest.utxoIndex = roundParams.getUtxoIdx();
        registerInputRequest.pubkey = simpleWhirlpoolClient.getPubkey();
        registerInputRequest.signature = simpleWhirlpoolClient.signMessage(roundStatusNotification.roundId);
        registerInputRequest.roundId = roundStatusNotification.roundId;
        registerInputRequest.paymentCode = roundParams.getPaymentCode();
        registerInputRequest.liquidity = roundParams.isLiquidity();

        // keep bordereau private, but transmit blindedBordereau
        // clear bordereau will be provided with unblindedBordereau under another identity for REGISTER_OUTPUT
        this.blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        this.bordereau = ClientUtils.generateUniqueBordereau();
        registerInputRequest.blindedBordereau = clientCryptoService.blind(this.bordereau, blindingParams);

        stompSession.send(whirlpoolProtocol.ENDPOINT_REGISTER_INPUT, registerInputRequest);
        roundStatusCompleted.put(RoundStatus.REGISTER_INPUT, true);
    }

    private void registerOutput(RegisterOutputRoundStatusNotification registerOutputRoundStatusNotification) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();
        ISimpleWhirlpoolClient simpleWhirlpoolClient = roundParams.getSimpleWhirlpoolClient();

        logStep(2, "REGISTER_OUTPUT");
        RegisterOutputRequest registerOutputRequest = new RegisterOutputRequest();
        registerOutputRequest.roundId = roundStatusNotification.roundId;
        registerOutputRequest.unblindedSignedBordereau = clientCryptoService.unblind(signedBordereau, blindingParams);
        registerOutputRequest.bordereau = this.bordereau;
        registerOutputRequest.sendAddress = simpleWhirlpoolClient.computeSendAddress(peersPaymentCodesResponse.toPaymentCode, networkParameters);
        this.receiveAddress = simpleWhirlpoolClient.computeReceiveAddress(peersPaymentCodesResponse.fromPaymentCode, networkParameters);
        registerOutputRequest.receiveAddress = this.receiveAddress;

        if (log.isDebugEnabled()) {
            log.debug("sendAddress=" + registerOutputRequest.sendAddress);
            log.debug("receiveAddress=" + registerOutputRequest.receiveAddress);
        }

        // POST request through a different identity for mix privacy
        try {
            simpleWhirlpoolClient.postHttpRequest(registerOutputRoundStatusNotification.getRegisterOutputUrl(), registerOutputRequest);
            roundStatusCompleted.put(RoundStatus.REGISTER_OUTPUT, true);
        }
        catch(Exception e) {
            log.error("failed to registerOutput", e);
            throw e;
        }
    }

    private void revealOutput() {

        logStep(3, "REVEAL_OUTPUT_OR_BLAME (round failed, someone didn't register output)");
        RevealOutputRequest revealOutputRequest = new RevealOutputRequest();
        revealOutputRequest.roundId = roundStatusNotification.roundId;
        revealOutputRequest.bordereau = this.bordereau;

        stompSession.send(whirlpoolProtocol.ENDPOINT_REVEAL_OUTPUT, revealOutputRequest);
        roundStatusCompleted.put(RoundStatus.REVEAL_OUTPUT_OR_BLAME, true);
    }

    private void signing(SigningRoundStatusNotification signingRoundStatusNotification) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();

        logStep(3, "SIGNING");
        SigningRequest signingRequest = new SigningRequest();
        signingRequest.roundId = roundStatusNotification.roundId;

        Transaction tx = new Transaction(networkParameters, signingRoundStatusNotification.transaction);

        Integer txOutputIndex = ClientUtils.findTxOutputIndex(this.receiveAddress, tx, networkParameters);
        if(txOutputIndex != null){
            receiveUtxoHash = tx.getHashAsString();
            receiveUtxoIdx = txOutputIndex;
        }
        else {
            throw new Exception("Output not found in tx");
        }

        Integer inputIndex = ClientUtils.findInputIndex(roundParams.getUtxoHash(), roundParams.getUtxoIdx(), tx);
        if (inputIndex == null) {
            throw new Exception("Input not found in tx");
        }
        long spendAmount = computeSpendAmount();
        roundParams.getSimpleWhirlpoolClient().signTransaction(tx, inputIndex, spendAmount, networkParameters);

        // verify
        tx.verify();

        // transmit
        signingRequest.witness = ClientUtils.witnessSerialize(tx.getWitness(inputIndex));
        stompSession.send(whirlpoolProtocol.ENDPOINT_SIGNING, signingRequest);
        roundStatusCompleted.put(RoundStatus.SIGNING, true);
    }

    private long computeSpendAmount() {
        if (roundParams.isLiquidity()) {
            // no minerFees for liquidities
            return denomination;
        }
        return denomination + minerFee;
    }

    private void logStep(int currentStep, String msg) {
        final int NB_STEPS = 4;
        log.info("("+currentStep+"/"+NB_STEPS+") " + msg);
        this.listener.progress(this.roundStatusNotification.status, currentStep, NB_STEPS);
    }

    public void exit() {
        disconnect();
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public void onTransportError(Throwable exception) {
        if (exception instanceof ConnectionLostException) {
            // ignore connectionLost when reconnecting (already managed)
            if (!reconnecting) {
                if (log.isDebugEnabled()) {
                    log.debug(" ! transportError : " + exception.getMessage());
                }
                onConnectionLost();
            }
        }
        else {
            log.error(" ! transportError : " + exception.getMessage());
        }
    }

    public void onAfterConnected(String stompUsername) {
        this.stompUsername = stompUsername;
    }

    private void onConnectionLost() {
        disconnect(true);

        if (hasRegisteredInput()) {
            log.error(" ! connection lost, reconnecting for resuming joined round...");
            this.resuming = true;
        }
        else {
            log.error(" ! connection lost, reconnecting for a new round...");
            resetRound();
        }
        reconnectOrExit();
    }

    private void reconnectOrExit() {
        try {
            reconnect();
        }
        catch(Exception e) {
            log.info(" ! Failed to connect to server. Please check your connectivity or retry later.");
            failAndExit();
        }
    }

    private void reconnect() throws Exception {
        reconnecting = true;
        long beginTime = System.currentTimeMillis();
        long elapsedTime;
        do {
            try {
                connectAndJoin();

                // success
                reconnecting = false;
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
        reconnecting = false;
        throw new Exception("Reconnecting failed");
    }

    public RoundStatusNotification __getRoundStatusNotification() {
        return roundStatusNotification;
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public void debugState() {
        if (log.isDebugEnabled()) {
            log.debug("stompUsername=" + stompUsername);
            log.debug("roundStatusComplete=" + roundStatusCompleted);
            log.debug("roundStatusNotification=" + ClientUtils.toJsonString(roundStatusNotification));
            if (errorResponse != null) {
                log.debug("errorResponse=" + ClientUtils.toJsonString(errorResponse));
            }
        }
    }
}
