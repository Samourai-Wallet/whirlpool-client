package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.services.ClientCryptoService;
import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;
import com.samourai.whirlpool.client.utils.ClientFrameHandler;
import com.samourai.whirlpool.client.utils.ClientSessionHandler;
import com.samourai.whirlpool.client.utils.ClientUtils;
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
    private String wsUrl;
    private NetworkParameters networkParameters;

    // round settings
    private String utxoHash;
    private long utxoIdx;
    private ISimpleWhirlpoolClient simpleWhirlpoolClient;
    private String paymentCode;
    private boolean liquidity;

    // round data
    private RoundStatusNotification roundStatusNotification;
    private RSAKeyParameters serverPublicKey;
    private String roundId;
    private long denomination;
    private long minerFee;
    private byte[] signedBordereau; // will get it after REGISTER_INPUT
    private PeersPaymentCodesResponse peersPaymentCodesResponse; // will get it after REGISTER_INPUT

    // computed values
    private String bordereau; // will generate it randomly
    private RSABlindingParameters blindingParams;
    private String receiveAddress;
    private Map<RoundStatus,Boolean> roundStatusCompleted = new HashMap<>();

    private ClientCryptoService clientCryptoService;
    private WhirlpoolProtocol whirlpoolProtocol;
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private boolean done;

    public WhirlpoolClient(String wsUrl, NetworkParameters networkParameters) {
        this(wsUrl, networkParameters, new ClientCryptoService(), new WhirlpoolProtocol());
    }

    public WhirlpoolClient(String wsUrl, NetworkParameters networkParameters, ClientCryptoService clientCryptoService, WhirlpoolProtocol whirlpoolProtocol) {
        this.wsUrl = wsUrl;
        this.networkParameters = networkParameters;
        this.clientCryptoService = clientCryptoService;
        this.whirlpoolProtocol = whirlpoolProtocol;
    }

    public void whirlpool(String utxoHash, long utxoIdx, String paymentCode, ISimpleWhirlpoolClient simpleWhirlpoolClient, boolean liquidity) throws Exception {
        this.utxoHash = utxoHash;
        this.utxoIdx = utxoIdx;
        this.simpleWhirlpoolClient = simpleWhirlpoolClient;
        this.paymentCode = paymentCode;
        this.liquidity = liquidity;

        connect();
        subscribe();
        getRoundStatus();
    }

    private void connect() throws Exception {
        if (this.stompClient != null) {
            log.warn("connect() : already connected");
            return;
        }

        log.info(" • connecting to "+wsUrl);
        stompClient = createWebSocketClient();
        stompSession = stompClient.connect(wsUrl, new ClientSessionHandler()).get();
        log.info(" • connected");

        // prefix logger
        log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()+"("+stompSession.getSessionId()+")");
    }

    public void disconnect() {
        log.info(" • disconnect");
        if (stompSession != null) {
            stompSession.disconnect();
            stompSession = null;
        }
        if (stompClient != null) {
            stompClient.stop();
            stompClient = null;
        }
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
            })
        );
    }

    private void getRoundStatus() {
        RoundStatusRequest roundStatusRequest = new RoundStatusRequest();
        stompSession.send(whirlpoolProtocol.ENDPOINT_ROUND_STATUS, roundStatusRequest);
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
        if (this.roundId != null && !this.roundId.equals(notification.roundId)) {
            // roundId changed, reset...
            log.info("new round detected: "+notification.roundId);
            this.resetRound();
        }
        if (this.roundStatusNotification == null || !notification.status.equals(this.roundStatusNotification.status)) {
            this.roundStatusNotification = notification;
            if (!roundStatusCompleted.containsKey(notification.status)) {
                switch (notification.status) {
                    case REGISTER_INPUT:
                        try {
                            this.registerInput((RegisterInputRoundStatusNotification)roundStatusNotification);
                        } catch (Exception e) {
                            log.error("registerInput failed", e);
                        }
                        break;
                    case REGISTER_OUTPUT:
                        registerOutputIfReady((RegisterOutputRoundStatusNotification)roundStatusNotification);
                        break;
                    case REVEAL_OUTPUT_OR_BLAME:
                        revealOutput();
                        break;
                    case SIGNING:
                        try {
                            this.signing((SigningRoundStatusNotification)roundStatusNotification);
                        } catch (Exception e) {
                            log.error("signing failed", e);
                        }
                        break;
                    case SUCCESS:
                        logStep(4, "SUCCESS");
                        log.info("Funds will be received at " + this.receiveAddress);
                        exit();
                        break;
                    case FAIL:
                        logStep(4, "FAILURE");
                        exit();
                        break;
                }
            }
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
        logStep(4, "ERROR");
        log.error(errorResponse.message);
        exit();
    }

    private void onRegisterInputResponse(RegisterInputResponse payload) {
        this.signedBordereau = payload.signedBordereau;
        if (RoundStatus.REGISTER_OUTPUT.equals(this.roundStatusNotification.status)) {
            registerOutputIfReady((RegisterOutputRoundStatusNotification)this.roundStatusNotification);
        }
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

    private void resetRound() {
        if (log.isDebugEnabled()) {
            log.debug("resetRound");
        }
        // round data
        this.roundStatusNotification = null;
        this.serverPublicKey = null;
        this.roundId = null;
        this.denomination = -1;
        this.minerFee = -1;
        this.signedBordereau = null;
        this.peersPaymentCodesResponse = null;

        // computed values
        this.bordereau = null;
        this.blindingParams = null;
        this.receiveAddress = null;
        this.roundStatusCompleted = new HashMap<>();
    }

    private void registerInput(RegisterInputRoundStatusNotification registerInputRoundStatusNotification) throws Exception {
        logStep(1, "REGISTER_INPUT");

        // get round settings
        this.serverPublicKey = ClientUtils.publicKeyUnserialize(registerInputRoundStatusNotification.getPublicKey());
        String serverNetworkId = registerInputRoundStatusNotification.getNetworkId();
        if (!networkParameters.getPaymentProtocolId().equals(serverNetworkId)) {
            throw new Exception("Client/server networkId mismatch: server is runinng "+serverNetworkId+", client is expecting "+networkParameters.getPaymentProtocolId());
        }
        this.denomination = registerInputRoundStatusNotification.getDenomination();
        this.minerFee = registerInputRoundStatusNotification.getMinerFee();

        RegisterInputRequest registerInputRequest = new RegisterInputRequest();
        registerInputRequest.utxoHash = utxoHash;
        registerInputRequest.utxoIndex = utxoIdx;
        registerInputRequest.pubkey = simpleWhirlpoolClient.getPubkey();
        registerInputRequest.signature = simpleWhirlpoolClient.signMessage(roundStatusNotification.roundId);
        registerInputRequest.roundId = roundStatusNotification.roundId;
        registerInputRequest.paymentCode = paymentCode;
        registerInputRequest.liquidity = this.liquidity;

        // keep bordereau private, but transmit blindedBordereau
        // clear bordereau will be provided with unblindedBordereau under another identity for REGISTER_OUTPUT
        this.blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        this.bordereau = ClientUtils.generateUniqueBordereau();
        registerInputRequest.blindedBordereau = clientCryptoService.blind(this.bordereau, blindingParams);

        stompSession.send(whirlpoolProtocol.ENDPOINT_REGISTER_INPUT, registerInputRequest);
        roundStatusCompleted.put(RoundStatus.REGISTER_INPUT, true);
    }

    private void registerOutput(RegisterOutputRoundStatusNotification registerOutputRoundStatusNotification) throws Exception {
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
            this.simpleWhirlpoolClient.postHttpRequest(registerOutputRoundStatusNotification.getRegisterOutputUrl(), registerOutputRequest);
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
        logStep(3, "SIGNING");
        SigningRequest signingRequest = new SigningRequest();
        signingRequest.roundId = roundStatusNotification.roundId;

        Transaction tx = new Transaction(networkParameters, signingRoundStatusNotification.transaction);

        if(!ClientUtils.findTxOutput(this.receiveAddress, tx, networkParameters)){
            throw new Exception("Output not found in tx"); //
        }

        Integer inputIndex = ClientUtils.findInputIndex(this.utxoHash, this.utxoIdx, tx);
        if (inputIndex == null) {
            throw new Exception("Input not found in tx");
        }
        long spendAmount = computeSpendAmount();
        this.simpleWhirlpoolClient.signTransaction(tx, inputIndex, spendAmount, networkParameters);

        // verify
        tx.verify();

        // transmit
        signingRequest.witness = ClientUtils.witnessSerialize(tx.getWitness(inputIndex));
        stompSession.send(whirlpoolProtocol.ENDPOINT_SIGNING, signingRequest);
        roundStatusCompleted.put(RoundStatus.SIGNING, true);
    }

    private long computeSpendAmount() {
        if (liquidity) {
            // no minerFees for liquidities
            return denomination;
        }
        return denomination + minerFee;
    }

    private void logStep(int step, String msg) {
        log.info("("+step+"/4) " + msg);
    }

    private void exit() {
        disconnect();
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public RoundStatusNotification __getRoundStatusNotification() {
        return roundStatusNotification;
    }
}
