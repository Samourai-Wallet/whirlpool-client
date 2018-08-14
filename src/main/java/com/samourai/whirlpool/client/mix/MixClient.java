package com.samourai.whirlpool.client.mix;

import ch.qos.logback.classic.Level;
import com.samourai.whirlpool.client.WhirlpoolClientConfig;
import com.samourai.whirlpool.client.beans.MixSuccess;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.handler.IMixHandler;
import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.websocket.ClientFrameHandler;
import com.samourai.whirlpool.client.websocket.ClientSessionHandler;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.*;
import com.samourai.whirlpool.protocol.websocket.notifications.*;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

public class MixClient {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // server settings
    private WhirlpoolClientConfig config;
    private String poolId;

    // mix settings
    private MixParams mixParams;
    private MixClientListener listener;

    // mix data
    private MixStatusNotification mixStatusNotification;
    private ErrorResponse errorResponse;
    private RSAKeyParameters serverPublicKey;
    private long denomination;
    private long minerFeeMin;
    private long minerFeeMax;
    private boolean liquidity;
    private byte[] signedBordereau; // will get it after REGISTER_INPUT
    private PeersPaymentCodesResponse peersPaymentCodesResponse; // will get it after REGISTER_INPUT

    // computed values
    private String bordereau; // will generate it randomly
    private RSABlindingParameters blindingParams;
    private String receiveAddress;
    private String receiveUtxoHash;
    private Integer receiveUtxoIdx;
    private Map<MixStatus,Boolean> mixStatusCompleted = new HashMap<>();

    private ClientCryptoService clientCryptoService;
    private WhirlpoolProtocol whirlpoolProtocol;
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private String stompUsername;
    private String logPrefix;
    private boolean reconnecting;
    private boolean resuming;
    private boolean done;

    public MixClient(WhirlpoolClientConfig config, String poolId) {
        this(config, poolId, new ClientCryptoService(), new WhirlpoolProtocol());
    }

    public MixClient(WhirlpoolClientConfig config, String poolId, ClientCryptoService clientCryptoService, WhirlpoolProtocol whirlpoolProtocol) {
        this.config = config;
        this.poolId = poolId;
        this.clientCryptoService = clientCryptoService;
        this.whirlpoolProtocol = whirlpoolProtocol;
    }

    public void whirlpool(MixParams mixParams, MixClientListener listener) {
        this.mixParams = mixParams;
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

            log.info(" • connecting to " + config.getServer());
            stompClient = createWebSocketClient();
            String wsUrl ="ws://" + config.getServer();
            StompHeaders stompHeaders = computeStompHeaders();
            stompSession = stompClient.connect(wsUrl, (WebSocketHttpHeaders) null, stompHeaders, new ClientSessionHandler(this)).get();

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

    private StompHeaders computeStompHeaders() {
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.set(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
        stompHeaders.set(WhirlpoolProtocol.HEADER_POOL_ID, poolId);
        return stompHeaders;
    }

    private StompHeaders computeStompHeaders(String destination) {
        StompHeaders stompHeaders = computeStompHeaders();
        stompHeaders.set(StompHeaders.DESTINATION, destination);
        return stompHeaders;
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
        stompSession.subscribe(computeStompHeaders(whirlpoolProtocol.SOCKET_SUBSCRIBE_QUEUE),
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
            }, (error) -> onProtocolError((String) error))
        );
        stompSession.subscribe(computeStompHeaders(whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_PRIVATE + whirlpoolProtocol.SOCKET_SUBSCRIBE_USER_REPLY),
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
            }, (error) -> onProtocolError((String) error))
        );
        // will automatically receive mixStatus in response of subscription
        if (log.isDebugEnabled()) {
            log.debug(" • subscribed to server");
        }
    }

    private void onBroadcastReceived(Object payload) {
        if (MixStatusNotification.class.isAssignableFrom(payload.getClass())) {
            onMixStatusNotificationChange((MixStatusNotification)payload);
        }
    }

    private synchronized void onMixStatusNotificationChange(MixStatusNotification notification) {
        try {
            if (this.mixStatusNotification != null && !this.mixStatusNotification.mixId.equals(notification.mixId)) {
                // mixId changed, reset...
                if (resuming) {
                    log.error(" ! Unable to resume joined mix: new mix detected");
                } else {
                    log.info("new mix detected: " + notification.mixId);
                }
                this.resetMix();
            }
            if (this.mixStatusNotification == null || !notification.status.equals(this.mixStatusNotification.status)) {
                this.mixStatusNotification = notification;
                // ignore duplicate mixStatus
                if (!mixStatusCompleted.containsKey(notification.status)) {

                    if (MixStatus.FAIL.equals(notification.status)) {
                        logStep(4, "FAILURE");
                        failAndExit();
                        return;
                    }

                    if (MixStatus.REGISTER_INPUT.equals(notification.status)) {
                        registerInput((RegisterInputMixStatusNotification) mixStatusNotification);
                        mixStatusCompleted.put(MixStatus.REGISTER_INPUT, true);

                    } else if (mixStatusCompleted.containsKey(MixStatus.REGISTER_INPUT)) {
                        if (gotRegisterInputResponse() && gotPeersPaymentCode()) {

                            if (MixStatus.REGISTER_OUTPUT.equals(notification.status)) {
                                this.registerOutput((RegisterOutputMixStatusNotification) mixStatusNotification);
                                mixStatusCompleted.put(MixStatus.REGISTER_OUTPUT, true);

                            } else if (mixStatusCompleted.containsKey(MixStatus.REGISTER_OUTPUT)) {
                                if (MixStatus.REVEAL_OUTPUT.equals(notification.status)) {
                                    this.revealOutput();
                                    mixStatusCompleted.put(MixStatus.REVEAL_OUTPUT, true);

                                } else if (MixStatus.SIGNING.equals(notification.status)) {
                                    this.signing((SigningMixStatusNotification) mixStatusNotification);
                                    mixStatusCompleted.put(MixStatus.SIGNING, true);

                                } else if (mixStatusCompleted.containsKey(MixStatus.SIGNING)) {

                                    if (MixStatus.SUCCESS.equals(notification.status)) {
                                        logStep(4, "SUCCESS");
                                        log.info("Funds will be received at " + this.receiveAddress + ", utxo " + this.receiveUtxoHash + ":" + this.receiveUtxoIdx);

                                        MixSuccess mixSuccess = new MixSuccess(this.receiveAddress, this.receiveUtxoHash, this.receiveUtxoIdx);
                                        this.listener.success(mixSuccess);
                                        exit();
                                        return;
                                    } else {

                                    }
                                } else {
                                    log.warn(" x SIGNING not completed");
                                    if (log.isDebugEnabled()) {
                                        log.error("Ignoring mixStatusNotification: " + ClientUtils.toJsonString(mixStatusNotification));
                                    }
                                }
                            } else {
                                log.warn(" x REGISTER_OUTPUT not completed");
                                if (log.isDebugEnabled()) {
                                    log.error("Ignoring mixStatusNotification: " + ClientUtils.toJsonString(mixStatusNotification));
                                }
                            }
                        } else {
                            if (liquidity) {
                                log.info(" > Ready to provide liquidity...");
                            } else {
                                log.info(" > Trying to join current mix...");
                            }
                        }
                    } else {
                        log.info(" > Waiting for next mix...");
                        if (log.isDebugEnabled()) {
                            log.debug("Current mix status: " + notification.status);
                        }
                    }
                }
                else {
                    log.warn("Ignoring duplicate mixStatus: "+mixStatusNotification.status);
                }
            }
        }
        catch(NotifiableException e) {
            onProtocolError(e.getMessage());
        }
        catch(Exception e) {
            log.error("", e);
            failAndExit();
        }
    }

    private synchronized void onPrivateReceived(Object payload) {
        Class payloadClass = payload.getClass();
        if (ErrorResponse.class.isAssignableFrom(payloadClass)) {
            onErrorResponse((ErrorResponse)payload);
        }
        else if (MixStatusNotification.class.isAssignableFrom(payload.getClass())) {
            onMixStatusNotificationChange((MixStatusNotification)payload);
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
        log.error("ERROR: " + errorResponse.message);
        failAndExit();
    }

    private void failAndExit() {
        this.listener.fail();
        exit();
    }

    private boolean gotRegisterInputResponse() {
        return (this.signedBordereau != null);
    }

    private void onRegisterInputResponse(RegisterInputResponse payload) {
        log.info(" > Joined mix " + this.mixStatusNotification.mixId);
        this.signedBordereau = payload.signedBordereau;
        if (MixStatus.REGISTER_OUTPUT.equals(this.mixStatusNotification.status)) {
            if (gotPeersPaymentCode()) {
                registerOutput((RegisterOutputMixStatusNotification) this.mixStatusNotification);
            }
        }
    }

    private boolean gotPeersPaymentCode() {
        return (this.peersPaymentCodesResponse != null);
    }

    private void onPeersPaymentCodeResponse(PeersPaymentCodesResponse payload) {
        this.peersPaymentCodesResponse = payload;
        if (MixStatus.REGISTER_OUTPUT.equals(this.mixStatusNotification.status)) {
            if (gotRegisterInputResponse()) {
                registerOutput((RegisterOutputMixStatusNotification) this.mixStatusNotification);
            }
        }
    }

    private WebSocketStompClient createWebSocketClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    public MixParams computeNextMixParams() {
        IMixHandler nextMixHandler = mixParams.getMixHandler().computeMixHandlerForNextMix();
        return new MixParams(this.receiveUtxoHash, this.receiveUtxoIdx, this.denomination, mixParams.getPaymentCode(), nextMixHandler);
    }

    private void resetMix() {
        if (log.isDebugEnabled()) {
            log.debug("resetMix");
        }
        // mix data
        this.mixStatusNotification = null;
        this.errorResponse = null;
        this.serverPublicKey = null;
        this.denomination = -1;
        this.minerFeeMin = -1;
        this.minerFeeMax = -1;
        this.signedBordereau = null;
        this.peersPaymentCodesResponse = null;

        // computed values
        this.bordereau = null;
        this.blindingParams = null;
        this.receiveAddress = null;
        this.receiveUtxoHash = null;
        this.receiveUtxoIdx = null;
        this.mixStatusCompleted = new HashMap<>();
        this.resuming = false;
    }

    private void registerInput(RegisterInputMixStatusNotification registerInputMixStatusNotification) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();

        // get mix settings
        this.serverPublicKey = ClientUtils.publicKeyUnserialize(registerInputMixStatusNotification.getPublicKey());
        String serverNetworkId = registerInputMixStatusNotification.getNetworkId();
        if (!networkParameters.getPaymentProtocolId().equals(serverNetworkId)) {
            throw new Exception("Client/server networkId mismatch: server is runinng "+serverNetworkId+", client is expecting "+networkParameters.getPaymentProtocolId());
        }
        this.denomination = registerInputMixStatusNotification.getDenomination();
        this.minerFeeMin = registerInputMixStatusNotification.getMinerFeeMin();
        this.minerFeeMax = registerInputMixStatusNotification.getMinerFeeMax();
        this.liquidity = mixParams.getUtxoBalance() == this.denomination;

        logStep(1, "REGISTER_INPUT (" + (this.liquidity ? "LIQUIDITY" : "MUSTMIX") + ")");

        checkUtxoBalance();

        IMixHandler mixHandler = mixParams.getMixHandler();

        RegisterInputRequest registerInputRequest = new RegisterInputRequest();
        registerInputRequest.utxoHash = mixParams.getUtxoHash();
        registerInputRequest.utxoIndex = mixParams.getUtxoIdx();
        registerInputRequest.pubkey = mixHandler.getPubkey();
        registerInputRequest.signature = mixHandler.signMessage(mixStatusNotification.mixId);
        registerInputRequest.mixId = mixStatusNotification.mixId;
        registerInputRequest.paymentCode = mixParams.getPaymentCode();
        registerInputRequest.liquidity = this.liquidity;

        // keep bordereau private, but transmit blindedBordereau
        // clear bordereau will be provided with unblindedBordereau under another identity for REGISTER_OUTPUT
        this.blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        this.bordereau = ClientUtils.generateUniqueBordereau();
        registerInputRequest.blindedBordereau = clientCryptoService.blind(this.bordereau, blindingParams);

        send(whirlpoolProtocol.ENDPOINT_REGISTER_INPUT, registerInputRequest);
    }

    private void checkUtxoBalance() throws NotifiableException {
        long inputBalanceMin = computeInputBalanceMin();
        long inputBalanceMax = computeInputBalanceMax();
        if (this.mixParams.getUtxoBalance() < inputBalanceMin) {
            throw new NotifiableException("Too low utxo-balance=" + this.mixParams.getUtxoBalance() + ". (expected: " + inputBalanceMin + " <= utxo-balance <= " + inputBalanceMax + ")");
        }

        if (this.mixParams.getUtxoBalance() > inputBalanceMax) {
            throw new NotifiableException("Too high utxo-balance=" + this.mixParams.getUtxoBalance() + ". (expected: " + inputBalanceMin + " <= utxo-balance <= " + inputBalanceMax + ")");
        }
    }

    private void registerOutput(RegisterOutputMixStatusNotification registerOutputMixStatusNotification) {
        try {
            NetworkParameters networkParameters = config.getNetworkParameters();
            IMixHandler mixHandler = mixParams.getMixHandler();

            logStep(2, "REGISTER_OUTPUT");
            RegisterOutputRequest registerOutputRequest = new RegisterOutputRequest();
            registerOutputRequest.mixId = mixStatusNotification.mixId;
            registerOutputRequest.unblindedSignedBordereau = clientCryptoService.unblind(signedBordereau, blindingParams);
            registerOutputRequest.bordereau = this.bordereau;
            registerOutputRequest.sendAddress = mixHandler.computeSendAddress(peersPaymentCodesResponse.toPaymentCode, networkParameters);
            this.receiveAddress = mixHandler.computeReceiveAddress(peersPaymentCodesResponse.fromPaymentCode, networkParameters);
            registerOutputRequest.receiveAddress = this.receiveAddress;

            if (log.isDebugEnabled()) {
                log.debug("sendAddress=" + registerOutputRequest.sendAddress);
                log.debug("receiveAddress=" + registerOutputRequest.receiveAddress);
                log.debug("POST " + registerOutputMixStatusNotification.getRegisterOutputUrl()+": " + ClientUtils.toJsonString(registerOutputRequest));
            }

            // POST request through a different identity for mix privacy
            mixHandler.postHttpRequest(registerOutputMixStatusNotification.getRegisterOutputUrl(), registerOutputRequest);

            if (log.isDebugEnabled()) {
                log.debug("POST completed");
            }
        }
        catch(Exception e) {
            log.error("failed to registerOutput", e);
            failAndExit();
        }
    }

    private void revealOutput() {

        logStep(3, "REVEAL_OUTPUT_OR_BLAME (mix failed, someone didn't register output)");
        RevealOutputRequest revealOutputRequest = new RevealOutputRequest();
        revealOutputRequest.mixId = mixStatusNotification.mixId;
        revealOutputRequest.bordereau = this.bordereau;

        send(whirlpoolProtocol.ENDPOINT_REVEAL_OUTPUT, revealOutputRequest);
    }

    private void signing(SigningMixStatusNotification signingMixStatusNotification) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();

        logStep(3, "SIGNING");
        SigningRequest signingRequest = new SigningRequest();
        signingRequest.mixId = mixStatusNotification.mixId;

        Transaction tx = new Transaction(networkParameters, signingMixStatusNotification.transaction);

        Integer txOutputIndex = ClientUtils.findTxOutputIndex(this.receiveAddress, tx, networkParameters);
        if(txOutputIndex != null){
            receiveUtxoHash = tx.getHashAsString();
            receiveUtxoIdx = txOutputIndex;
        }
        else {
            throw new Exception("Output not found in tx");
        }

        Integer inputIndex = ClientUtils.findInputIndex(mixParams.getUtxoHash(), mixParams.getUtxoIdx(), tx);
        if (inputIndex == null) {
            throw new Exception("Input not found in tx");
        }
        long spendAmount = mixParams.getUtxoBalance();
        mixParams.getMixHandler().signTransaction(tx, inputIndex, spendAmount, networkParameters);

        // verify
        tx.verify();

        // transmit
        signingRequest.witness = ClientUtils.witnessSerialize(tx.getWitness(inputIndex));
        send(whirlpoolProtocol.ENDPOINT_SIGNING, signingRequest);
    }

    private void logStep(int currentStep, String msg) {
        final int NB_STEPS = 4;
        log.info("("+currentStep+"/"+NB_STEPS+") " + msg);
        MixStatus mixStatus = (this.mixStatusNotification != null ? this.mixStatusNotification.status : MixStatus.REGISTER_INPUT); // null when protocol error on REGISTER_INPUT
        this.listener.progress(mixStatus, currentStep, NB_STEPS);
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

    private void onProtocolError(String errorMessage) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.message = errorMessage; // protocol version mismatch
        onErrorResponse(errorResponse);
    }

    public void onAfterConnected(String stompUsername) {
        this.stompUsername = stompUsername;
    }

    private void onConnectionLost() {
        disconnect(true);

        if (gotRegisterInputResponse()) {
            log.error(" ! connection lost, reconnecting for resuming joined mix...");
            this.resuming = true;
        }
        else {
            log.error(" ! connection lost, reconnecting for a new mix...");
            resetMix();
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

    private void send(String destination, Object message) {
        StompHeaders stompHeaders = new StompHeaders();
        stompHeaders.setDestination(destination);
        stompHeaders.set(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION, WhirlpoolProtocol.PROTOCOL_VERSION);
        stompSession.send(stompHeaders, message);
    }

    private long computeInputBalanceMin() {
        return WhirlpoolProtocol.computeInputBalanceMin(denomination, liquidity, minerFeeMin);
    }

    private long computeInputBalanceMax() {
        return WhirlpoolProtocol.computeInputBalanceMax(denomination, liquidity, minerFeeMax);
    }

    public MixStatusNotification __getMixStatusNotification() {
        return mixStatusNotification;
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public void debugState() {
        if (log.isDebugEnabled()) {
            log.debug("stompUsername=" + stompUsername);
            log.debug("mixStatusComplete=" + mixStatusCompleted);
            log.debug("mixStatusNotification=" + ClientUtils.toJsonString(mixStatusNotification));
            if (errorResponse != null) {
                log.debug("errorResponse=" + ClientUtils.toJsonString(errorResponse));
            }
        }
    }
}
