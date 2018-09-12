package com.samourai.whirlpool.client.mix;

import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.handler.IMixHandler;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientCryptoService;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputResponse;
import com.samourai.whirlpool.protocol.websocket.messages.RevealOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.SigningRequest;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;
import org.bitcoinj.core.*;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class MixProcess {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WhirlpoolClientConfig config;
    private MixParams mixParams;
    private ClientCryptoService clientCryptoService;
    private long denomination;

    // hard limit for acceptable fees
    private static final long MAX_ACCEPTABLE_FEES = 100000;

    // mix data
    private byte[] signedBordereau; // will get it on RegisterInputResponse
    private String inputsHash; // will get it on REGISTER_OUTPUT

    // computed values
    private boolean liquidity;
    private RSABlindingParameters blindingParams;
    private String receiveAddress;
    private String receiveUtxoHash;
    private Integer receiveUtxoIdx;

    // security checks
    private boolean registeredInput;
    private boolean registeredInputResponse;
    private boolean registeredOutput;
    private boolean revealedOutput;
    private boolean signed;


    public MixProcess(WhirlpoolClientConfig config, MixParams mixParams, ClientCryptoService clientCryptoService, long denomination) {
        this.config = config;
        this.mixParams = mixParams;
        this.clientCryptoService = clientCryptoService;
        this.denomination = denomination;
    }

    protected RegisterInputRequest registerInput(RegisterInputMixStatusNotification registerInputMixStatusNotification) throws Exception {
        if (registeredInput || registeredInputResponse || registeredOutput || revealedOutput || signed) {
            throwProtocolException();
        }

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
        this.liquidity = mixParams.getUtxoBalance() == denomination;

        if (log.isDebugEnabled()) {
            log.debug("Registering input as " + (this.liquidity ? "LIQUIDITY" : "MUSTMIX"));
        }

        // verify fees acceptable
        checkFees(mixParams.getUtxoBalance(), denomination);

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

        registeredInput = true;
        return registerInputRequest;
    }

    protected void onRegisterInputResponse(RegisterInputResponse registerInputResponse) throws Exception {
        if (!registeredInput || registeredInputResponse || registeredOutput || revealedOutput || signed) {
            throwProtocolException();
        }

        this.signedBordereau = registerInputResponse.signedBordereau;

        registeredInputResponse = true;
    }

    protected RegisterOutputRequest registerOutput(RegisterOutputMixStatusNotification registerOutputMixStatusNotification) throws Exception {
        if (!registeredInput || !registeredInputResponse || registeredOutput || revealedOutput || signed) {
            throwProtocolException();
        }

        this.inputsHash = registerOutputMixStatusNotification.getInputsHash();

        RegisterOutputRequest registerOutputRequest = new RegisterOutputRequest();
        registerOutputRequest.inputsHash = inputsHash;
        registerOutputRequest.unblindedSignedBordereau = clientCryptoService.unblind(signedBordereau, blindingParams);
        registerOutputRequest.receiveAddress = this.receiveAddress;

        registeredOutput = true;
        return registerOutputRequest;
    }

    protected RevealOutputRequest revealOutput(RevealOutputMixStatusNotification revealOutputMixStatusNotification) throws Exception {
        if (!registeredInput || !registeredInputResponse || !registeredOutput || revealedOutput || signed) {
            throwProtocolException();
        }

        RevealOutputRequest revealOutputRequest = new RevealOutputRequest();
        revealOutputRequest.mixId = revealOutputMixStatusNotification.mixId;
        revealOutputRequest.receiveAddress = this.receiveAddress;

        revealedOutput = true;
        return revealOutputRequest;
    }

    protected SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification) throws Exception {
        if (!registeredInput || !registeredInputResponse || !registeredOutput || revealedOutput || signed) {
            throwProtocolException();
        }

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

        signed = true;
        return signingRequest;
    }

    protected MixParams computeNextMixParams() {
        IMixHandler nextMixHandler = mixParams.getMixHandler().computeMixHandlerForNextMix();
        return new MixParams(this.receiveUtxoHash, this.receiveUtxoIdx, this.denomination, nextMixHandler);
    }

    protected MixSuccess computeMixSuccess() {
        return new MixSuccess(this.receiveAddress, this.receiveUtxoHash, this.receiveUtxoIdx);
    }

    //

    private void checkFees(long inputValue, long outputValue) throws NotifiableException {
        long fees = inputValue - outputValue;

        if (liquidity && fees > 0) {
            throw new NotifiableException("Should not pay fees as a liquidity");
        }
        if (fees > MAX_ACCEPTABLE_FEES) {
            log.error("Fees abnormally abnormally: fees=" + fees + ", MAX_ACCEPTABLE_FEES=" + MAX_ACCEPTABLE_FEES);
            throw new NotifiableException("Fees abnormally high");
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

    private int verifyTx(Transaction tx) throws Exception {
        NetworkParameters networkParameters = config.getNetworkParameters();

        // verify inputsHash
        String txInputsHash = computeInputsHash(tx.getInputs());
        if (!txInputsHash.equals(inputsHash)) {
            throw new Exception("Inputs hash mismatch. Aborting.");
        }

        // verify my output
        Integer outputIndex = ClientUtils.findTxOutputIndex(this.receiveAddress, tx, networkParameters);
        if (outputIndex == null) {
            throw new Exception("Output not found in tx");
        }
        receiveUtxoHash = tx.getHashAsString();
        receiveUtxoIdx = outputIndex;

        // verify my input
        Integer inputIndex = ClientUtils.findTxInputIndex(mixParams.getUtxoHash(), mixParams.getUtxoIdx(), tx);
        if (outputIndex == null) {
            throw new Exception("Input not found in tx");
        }

        // check fees again
        long inputValue = mixParams.getUtxoBalance(); //tx.getInput(inputIndex).getValue().getValue(); is null
        long outputValue = tx.getOutput(outputIndex).getValue().getValue();
        checkFees(inputValue, outputValue);

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

    private String computeInputsHash(List<TransactionInput> inputs) {
        List<Utxo> utxos = new ArrayList<>();
        for (TransactionInput input : inputs) {
            Utxo utxo = new Utxo(input.getOutpoint().getHash().toString(), input.getOutpoint().getIndex());
            utxos.add(utxo);
        }
        return WhirlpoolProtocol.computeInputsHash(utxos);
    }

    private boolean throwProtocolException() throws Exception {
        String message = "Protocol exception: "
                + " registeredInput=" + registeredInput
                + ", registeredInputResponse=" + registeredInputResponse
                + ", registeredOutput" + registeredOutput
                + ", revealedOutput" + revealedOutput
                + ", signed=" + signed;
        throw new ProtocolException(message);
    }
}
