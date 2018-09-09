package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.handler.IMixHandler;
import com.samourai.whirlpool.client.mix.listener.MixClientListener;
import com.samourai.whirlpool.client.mix.listener.MixClientListenerHandler;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.mix.transport.MixDialogListener;
import com.samourai.whirlpool.client.utils.ClientCryptoService;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.*;
import com.samourai.whirlpool.protocol.websocket.notifications.*;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MixClient {
    // non-static logger to prefix it with stomp sessionId
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    // hard limit for acceptable fees
    private static final long MAX_ACCEPTABLE_FEES = 100000;

    // server settings
    private WhirlpoolClientConfig config;
    private String poolId;
    private long denomination;

    // mix settings
    private MixParams mixParams;
    private MixClientListenerHandler listener;

    // mix data
    private byte[] signedBordereau; // will get it on RegisterInputResponse
    private String inputsHash; // will get it on REGISTER_OUTPUT

    // computed values
    private boolean liquidity;
    private RSABlindingParameters blindingParams;
    private String receiveAddress;
    private String receiveUtxoHash;
    private Integer receiveUtxoIdx;

    private ClientCryptoService clientCryptoService;
    private WhirlpoolProtocol whirlpoolProtocol;
    private MixSession mixSession;
    private boolean done;

    public MixClient(WhirlpoolClientConfig config, String poolId, long denomination) {
        this(config, poolId, denomination, new ClientCryptoService(), new WhirlpoolProtocol());
    }

    public MixClient(WhirlpoolClientConfig config, String poolId, long denomination, ClientCryptoService clientCryptoService, WhirlpoolProtocol whirlpoolProtocol) {
        this.config = config;
        this.poolId = poolId;
        this.denomination = denomination;
        this.clientCryptoService = clientCryptoService;
        this.whirlpoolProtocol = whirlpoolProtocol;
    }

    public void whirlpool(MixParams mixParams, MixClientListener listener) {
        this.mixParams = mixParams;
        this.listener = new MixClientListenerHandler(listener);

        connect();
    }

    private void listenerProgress(MixStep mixClientStatus) {
        this.listener.progress(mixClientStatus);
    }

    private void connect() {
        if (this.mixSession != null) {
            log.warn("connect() : already connected");
            return;
        }

        listenerProgress(MixStep.CONNECTING);
        try {
            mixSession = new MixSession(computeMixDialogListener(), whirlpoolProtocol, config, poolId);
            mixSession.connect();

            listenerProgress(MixStep.CONNECTED);
        } catch(Exception e) {
            log.error("Unable to connect", e);
            failAndExit();
        }
    }

    private void disconnect() {
        if (mixSession != null) {
            mixSession.disconnect();
            mixSession = null;
        }
    }

    private void failAndExit() {
        this.listener.progress(MixStep.FAIL);
        this.listener.fail();
        exit();
    }

    private void onResetMix() {
        if (log.isDebugEnabled()) {
            log.debug("resetMix");
        }

        // mix data
        this.signedBordereau = null;
        this.inputsHash = null;

        // computed values
        this.liquidity = false;
        this.blindingParams = null;
        this.receiveAddress = null;
        this.receiveUtxoHash = null;
        this.receiveUtxoIdx = null;
    }

    private RegisterInputRequest registerInput(RegisterInputMixStatusNotification registerInputMixStatusNotification) throws Exception {
        listenerProgress(MixStep.REGISTERING_INPUT);

        // check denomination
        long actualDenomination = registerInputMixStatusNotification.getDenomination();
        if (denomination != actualDenomination) {
            log.error("Invalid denomination: expected=" + denomination + ", actual=" + actualDenomination);
            throw new NotifiableException("Unexpected denomination from server");
        }

        // get mix settings
        NetworkParameters networkParameters = config.getNetworkParameters();
        String serverNetworkId = registerInputMixStatusNotification.getNetworkId();
        if (!networkParameters.getPaymentProtocolId().equals(serverNetworkId)) {
            throw new Exception("Client/server networkId mismatch: server is runinng "+serverNetworkId+", client is expecting "+networkParameters.getPaymentProtocolId());
        }
        this.liquidity = mixParams.getUtxoBalance() == this.denomination;

        if (log.isDebugEnabled()) {
            log.debug("Registering input as " + (this.liquidity ? "LIQUIDITY" : "MUSTMIX"));
        }

        // verify fees acceptable
        checkDenomination();

        // verify balance
        long minerFeeMin = registerInputMixStatusNotification.getMinerFeeMin();
        long minerFeeMax = registerInputMixStatusNotification.getMinerFeeMax();
        checkUtxoBalance(minerFeeMin, minerFeeMax);

        IMixHandler mixHandler = mixParams.getMixHandler();
        String mixId = registerInputMixStatusNotification.mixId;

        RegisterInputRequest registerInputRequest = new RegisterInputRequest();
        registerInputRequest.utxoHash = mixParams.getUtxoHash();
        registerInputRequest.utxoIndex = mixParams.getUtxoIdx();
        registerInputRequest.pubkey = mixHandler.getPubkey();
        registerInputRequest.signature = mixHandler.signMessage(mixId);
        registerInputRequest.mixId = mixId;
        registerInputRequest.liquidity = this.liquidity;
        registerInputRequest.testMode = config.isTestMode();

        // use receiveAddress as bordereau. keep it private, but transmit blindedBordereau
        // clear receiveAddress will be provided with unblindedSignedBordereau by connecting with another identity for REGISTER_OUTPUT
        RSAKeyParameters serverPublicKey = ClientUtils.publicKeyUnserialize(registerInputMixStatusNotification.getPublicKey());
        this.blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
        this.receiveAddress = mixHandler.computeReceiveAddress(networkParameters);
        registerInputRequest.blindedBordereau = clientCryptoService.blind(this.receiveAddress, blindingParams);
        return registerInputRequest;
    }

    private void checkDenomination() throws NotifiableException {
        long myFees = mixParams.getUtxoBalance() - denomination;
        if (myFees > MAX_ACCEPTABLE_FEES) {
            log.error("Fees too high, aborting. myFees=" + myFees + ", MAX_ACCEPTABLE_FEES=" + MAX_ACCEPTABLE_FEES);
            throw new NotifiableException("Fees too high, aborting.");
        }
    }

    private void checkUtxoBalance(long minerFeeMin, long minerFeeMax) throws NotifiableException {
        long inputBalanceMin = WhirlpoolProtocol.computeInputBalanceMin(denomination, liquidity, minerFeeMin);
        long inputBalanceMax = WhirlpoolProtocol.computeInputBalanceMax(denomination, liquidity, minerFeeMax);
        if (this.mixParams.getUtxoBalance() < inputBalanceMin) {
            throw new NotifiableException("Too low utxo-balance=" + this.mixParams.getUtxoBalance() + ". (expected: " + inputBalanceMin + " <= utxo-balance <= " + inputBalanceMax + ")");
        }

        if (this.mixParams.getUtxoBalance() > inputBalanceMax) {
            throw new NotifiableException("Too high utxo-balance=" + this.mixParams.getUtxoBalance() + ". (expected: " + inputBalanceMin + " <= utxo-balance <= " + inputBalanceMax + ")");
        }
    }

    private void postRegisterOutput(RegisterOutputMixStatusNotification registerOutputMixStatusNotification, String registerOutputUrl) throws Exception {
        listenerProgress(MixStep.REGISTERING_OUTPUT);
        this.inputsHash = registerOutputMixStatusNotification.getInputsHash();

        RegisterOutputRequest registerOutputRequest = new RegisterOutputRequest();
        registerOutputRequest.inputsHash = inputsHash;
        registerOutputRequest.unblindedSignedBordereau = clientCryptoService.unblind(signedBordereau, blindingParams);
        registerOutputRequest.receiveAddress = this.receiveAddress;

        // POST request through a different identity for mix privacy
        if (log.isDebugEnabled()) {
            log.debug("POST " + registerOutputUrl + ": " + ClientUtils.toJsonString(registerOutputRequest));
        }
        mixParams.getMixHandler().postHttpRequest(registerOutputUrl, registerOutputRequest);

        listenerProgress(MixStep.REGISTERED_OUTPUT);
    }

    private RevealOutputRequest revealOutput(RevealOutputMixStatusNotification revealOutputMixStatusNotification) {
        listenerProgress(MixStep.REVEALING_OUTPUT);

        RevealOutputRequest revealOutputRequest = new RevealOutputRequest();
        revealOutputRequest.mixId = revealOutputMixStatusNotification.mixId;
        revealOutputRequest.receiveAddress = this.receiveAddress;

        listenerProgress(MixStep.REVEALED_OUTPUT);
        return revealOutputRequest;
    }

    private SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification) throws Exception {
        listenerProgress(MixStep.SIGNING);
        NetworkParameters networkParameters = config.getNetworkParameters();

        SigningRequest signingRequest = new SigningRequest();
        signingRequest.mixId = signingMixStatusNotification.mixId;

        Transaction tx = new Transaction(networkParameters, signingMixStatusNotification.transaction);

        // verify tx
        int inputIndex = verifyTx(tx);

        long spendAmount = mixParams.getUtxoBalance();
        mixParams.getMixHandler().signTransaction(tx, inputIndex, spendAmount, networkParameters);

        // verify signature
        tx.verify();

        // transmit
        signingRequest.witness = ClientUtils.witnessSerialize(tx.getWitness(inputIndex));

        listenerProgress(MixStep.SIGNED);
        return signingRequest;
    }

    private int verifyTx(Transaction tx) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();

        // verify inputsHash
        String txInputsHash = computeInputsHash(tx.getInputs());
        if (!txInputsHash.equals(inputsHash)) {
            throw new Exception("Inputs hash mismatch. Aborting.");
        }

        // verify my output
        int txOutputIndex = ClientUtils.findTxOutputIndex(this.receiveAddress, tx, networkParameters).orElseThrow(() -> new Exception("Output not found in tx"));
        receiveUtxoHash = tx.getHashAsString();
        receiveUtxoIdx = txOutputIndex;

        // verify my input
        int inputIndex = ClientUtils.findTxInputIndex(mixParams.getUtxoHash(), mixParams.getUtxoIdx(), tx).orElseThrow(() -> new Exception("Input not found in tx"));

        // as many inputs as outputs
        if (tx.getInputs().size() != tx.getOutputs().size()) {
            log.error("inputs.size = " + tx.getInputs().size() + ", outputs.size=" + tx.getOutputs().size());
            throw new Exception("Inputs size vs outputs size mismatch");
        }

        // each output value should be denomination
        for (TransactionOutput output : tx.getOutputs()) {
            if (output.getValue().getValue() != denomination) {
                log.error("outputValue=" + output.getValue().getValue() + ", denomination=" + denomination);
                throw new Exception("Output value mismatch");
            }
        }

        return inputIndex;
    }

    private void onRegisterInputResponse(RegisterInputResponse registerInputResponse) {
        this.signedBordereau = registerInputResponse.signedBordereau;

        listenerProgress(MixStep.REGISTERED_INPUT);
        if (log.isDebugEnabled()) {
            log.debug("joined mix: mixId=" + registerInputResponse.mixId);
        }
    }

    private void onLiquidityQueuedResponse(LiquidityQueuedResponse liquidityQueuedResponse) {
        listenerProgress(MixStep.QUEUED_LIQUIDITY);
    }

    private void onSuccess() {
        this.listener.progress(MixStep.SUCCESS);
        MixSuccess mixSuccess = new MixSuccess(this.receiveAddress, this.receiveUtxoHash, this.receiveUtxoIdx);
        MixParams nextMixParams = computeNextMixParams();
        this.listener.success(mixSuccess, nextMixParams);
        exit();
    }

    private MixParams computeNextMixParams() {
        IMixHandler nextMixHandler = mixParams.getMixHandler().computeMixHandlerForNextMix();
        return new MixParams(this.receiveUtxoHash, this.receiveUtxoIdx, this.denomination, nextMixHandler);
    }

    private String computeInputsHash(List<TransactionInput> inputs) {
        Collection<Utxo> inputsUtxos = inputs.parallelStream().map(input -> new Utxo(input.getOutpoint().getHash().toString(), input.getOutpoint().getIndex())).collect(Collectors.toList());
        return WhirlpoolProtocol.computeInputsHash(inputsUtxos);
    }

    public void exit() {
        disconnect();
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public void setLogPrefix(String logPrefix) {
        log = ClientUtils.prefixLogger(log, logPrefix);
    }

    private MixDialogListener computeMixDialogListener() {
        return new MixDialogListener() {
            @Override
            public void onFail() {
                failAndExit();
            }

            @Override
            public void exitOnProtocolError() {
                log.error("ERROR: protocol error, this may be a bug");
                failAndExit();
            }

            @Override
            public void exitOnResponseError(String notifiableError) {
                log.error("ERROR: " + notifiableError);
                failAndExit();
            }

            @Override
            public void exitOnConnectionLost() {
                log.error("Connection lost");
                failAndExit();
            }

            @Override
            public RegisterInputRequest registerInput(RegisterInputMixStatusNotification registerInputMixStatusNotification) throws Exception {
                return MixClient.this.registerInput(registerInputMixStatusNotification);
            }

            @Override
            public void postRegisterOutput(RegisterOutputMixStatusNotification registerOutputMixStatusNotification, String registerOutputUrl) throws Exception {
                MixClient.this.postRegisterOutput(registerOutputMixStatusNotification, registerOutputUrl);
            }

            @Override
            public RevealOutputRequest revealOutput(RevealOutputMixStatusNotification revealOutputMixStatusNotification) throws Exception {
                return MixClient.this.revealOutput(revealOutputMixStatusNotification);
            }

            @Override
            public SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification) throws Exception {
                return MixClient.this.signing(signingMixStatusNotification);
            }

            @Override
            public void onRegisterInputResponse(RegisterInputResponse registerInputResponse) {
                MixClient.this.onRegisterInputResponse(registerInputResponse);
            }

            @Override
            public void onLiquidityQueuedResponse(LiquidityQueuedResponse liquidityQueuedResponse) {
                MixClient.this.onLiquidityQueuedResponse(liquidityQueuedResponse);
            }

            @Override
            public void onSuccess() {
                MixClient.this.onSuccess();
            }

            @Override
            public void onResetMix() {
                MixClient.this.onResetMix();
            }
        };
    }
}
