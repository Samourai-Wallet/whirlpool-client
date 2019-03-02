package com.samourai.whirlpool.client.mix;

import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.handler.IPostmixHandler;
import com.samourai.whirlpool.client.mix.handler.IPremixHandler;
import com.samourai.whirlpool.client.mix.handler.UtxoWithBalance;
import com.samourai.whirlpool.client.mix.listener.MixSuccess;
import com.samourai.whirlpool.client.utils.ClientCryptoService;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.whirlpool.WhirlpoolClientConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.rest.RegisterOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.ConfirmInputResponse;
import com.samourai.whirlpool.protocol.websocket.messages.RegisterInputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.RevealOutputRequest;
import com.samourai.whirlpool.protocol.websocket.messages.SigningRequest;
import com.samourai.whirlpool.protocol.websocket.messages.SubscribePoolResponse;
import com.samourai.whirlpool.protocol.websocket.notifications.ConfirmInputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RegisterOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.RevealOutputMixStatusNotification;
import com.samourai.whirlpool.protocol.websocket.notifications.SigningMixStatusNotification;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MixProcess {
  private static final Logger log = LoggerFactory.getLogger(MixProcess.class);
  private WhirlpoolClientConfig config;
  private String poolId;
  private long poolDenomination;
  private IPremixHandler premixHandler;
  private IPostmixHandler postmixHandler;
  private ClientCryptoService clientCryptoService;

  // hard limit for acceptable fees
  private static final long MAX_ACCEPTABLE_FEES = 100000;

  // mix data
  private byte[] signedBordereau; // will get it on RegisterInputResponse
  private String inputsHash; // will get it on REGISTER_OUTPUT

  // computed values
  private boolean liquidity;
  private RSABlindingParameters blindingParams;
  private String receiveAddress;
  private Utxo receiveUtxo;

  // security checks
  private boolean registeredInput;
  private boolean confirmedInput;
  private boolean confirmedInputResponse;
  private boolean registeredOutput;
  private boolean revealedOutput;
  private boolean signed;

  public MixProcess(
      WhirlpoolClientConfig config,
      String poolId,
      long poolDenomination,
      IPremixHandler premixHandler,
      IPostmixHandler postmixHandler,
      ClientCryptoService clientCryptoService) {
    this.config = config;
    this.poolId = poolId;
    this.poolDenomination = poolDenomination;
    this.premixHandler = premixHandler;
    this.postmixHandler = postmixHandler;
    this.clientCryptoService = clientCryptoService;
  }

  protected RegisterInputRequest registerInput(SubscribePoolResponse subscribePoolResponse)
      throws Exception {

    // we may registerInput several times if disconnected
    if (
    /*registeredInput ||*/ confirmedInput
        || confirmedInputResponse
        || registeredOutput
        || revealedOutput
        || signed) {
      throwProtocolException();
    }

    // check denomination
    long actualDenomination = subscribePoolResponse.denomination;
    if (poolDenomination != actualDenomination) {
      log.error(
          "Invalid denomination: expected=" + poolDenomination + ", actual=" + actualDenomination);
      throw new NotifiableException("Unexpected denomination from server");
    }

    // get mix settings
    UtxoWithBalance utxo = premixHandler.getUtxo();
    NetworkParameters networkParameters = config.getNetworkParameters();
    String serverNetworkId = subscribePoolResponse.networkId;
    if (!networkParameters.getPaymentProtocolId().equals(serverNetworkId)) {
      throw new Exception(
          "Client/server networkId mismatch: server is runinng "
              + serverNetworkId
              + ", client is expecting "
              + networkParameters.getPaymentProtocolId());
    }
    this.liquidity = utxo.getBalance() == poolDenomination;

    if (log.isDebugEnabled()) {
      log.debug("Registering input as " + (this.liquidity ? "LIQUIDITY" : "MUSTMIX"));
    }

    // verify fees acceptable
    checkFees(utxo.getBalance(), poolDenomination);

    // verify balance
    long mustMixBalanceMin = subscribePoolResponse.mustMixBalanceMin;
    long mustMixBalanceMax = subscribePoolResponse.mustMixBalanceMax;
    checkUtxoBalance(mustMixBalanceMin, mustMixBalanceMax);

    String signature = premixHandler.signMessage(poolId);
    RegisterInputRequest registerInputRequest =
        new RegisterInputRequest(
            poolId,
            utxo.getHash(),
            utxo.getIndex(),
            signature,
            this.liquidity,
            config.isTestMode());

    registeredInput = true;
    return registerInputRequest;
  }

  protected ConfirmInputRequest confirmInput(
      ConfirmInputMixStatusNotification confirmInputMixStatusNotification) throws Exception {
    // we may confirmInput several times before getting confirmedInputResponse
    if (!registeredInput
        || /*confirmedInput ||*/ confirmedInputResponse
        || registeredOutput
        || revealedOutput
        || signed) {
      throwProtocolException();
    }

    NetworkParameters networkParameters = config.getNetworkParameters();

    // use receiveAddress as bordereau. keep it private, but transmit blindedBordereau
    // clear receiveAddress will be provided with unblindedSignedBordereau by connecting with
    // another identity for REGISTER_OUTPUT
    byte[] publicKey = WhirlpoolProtocol.decodeBytes(confirmInputMixStatusNotification.publicKey64);
    RSAKeyParameters serverPublicKey = ClientUtils.publicKeyUnserialize(publicKey);
    this.blindingParams = clientCryptoService.computeBlindingParams(serverPublicKey);
    this.receiveAddress = postmixHandler.computeReceiveAddress(networkParameters);

    String mixId = confirmInputMixStatusNotification.mixId;
    String blindedBordereau64 =
        WhirlpoolProtocol.encodeBytes(
            clientCryptoService.blind(this.receiveAddress, blindingParams));
    ConfirmInputRequest confirmInputRequest = new ConfirmInputRequest(mixId, blindedBordereau64);

    confirmedInput = true;
    return confirmInputRequest;
  }

  protected void onConfirmInputResponse(ConfirmInputResponse confirmInputResponse)
      throws Exception {
    if (!registeredInput
        || !confirmedInput
        || confirmedInputResponse
        || registeredOutput
        || revealedOutput
        || signed) {
      throwProtocolException();
    }

    this.signedBordereau = WhirlpoolProtocol.decodeBytes(confirmInputResponse.signedBordereau64);

    confirmedInputResponse = true;
  }

  protected RegisterOutputRequest registerOutput(
      RegisterOutputMixStatusNotification registerOutputMixStatusNotification) throws Exception {
    if (!registeredInput
        || !confirmedInput
        || !confirmedInputResponse
        || registeredOutput
        || revealedOutput
        || signed) {
      throwProtocolException();
    }

    this.inputsHash = registerOutputMixStatusNotification.getInputsHash();

    String unblindedSignedBordereau64 =
        WhirlpoolProtocol.encodeBytes(clientCryptoService.unblind(signedBordereau, blindingParams));
    RegisterOutputRequest registerOutputRequest =
        new RegisterOutputRequest(inputsHash, unblindedSignedBordereau64, this.receiveAddress);

    registeredOutput = true;
    return registerOutputRequest;
  }

  protected RevealOutputRequest revealOutput(
      RevealOutputMixStatusNotification revealOutputMixStatusNotification) throws Exception {
    if (!registeredInput
        || !confirmedInput
        || !confirmedInputResponse
        || !registeredOutput
        || revealedOutput
        || signed) {
      throwProtocolException();
    }

    RevealOutputRequest revealOutputRequest =
        new RevealOutputRequest(revealOutputMixStatusNotification.mixId, this.receiveAddress);

    revealedOutput = true;
    return revealOutputRequest;
  }

  protected SigningRequest signing(SigningMixStatusNotification signingMixStatusNotification)
      throws Exception {
    if (!registeredInput
        || !confirmedInput
        || !confirmedInputResponse
        || !registeredOutput
        || revealedOutput
        || signed) {
      throwProtocolException();
    }

    NetworkParameters networkParameters = config.getNetworkParameters();

    byte[] rawTx = WhirlpoolProtocol.decodeBytes(signingMixStatusNotification.transaction64);
    Transaction tx = new Transaction(networkParameters, rawTx);

    // verify tx
    int inputIndex = verifyTx(tx);

    premixHandler.signTransaction(tx, inputIndex, networkParameters);

    // verify signature
    tx.verify();

    // transmit
    String[] witnesses64 = ClientUtils.witnessSerialize64(tx.getWitness(inputIndex));
    SigningRequest signingRequest =
        new SigningRequest(signingMixStatusNotification.mixId, witnesses64);

    signed = true;
    return signingRequest;
  }

  protected IPremixHandler computeNextPremixHandler() {
    UtxoWithBalance utxoWithBalance = new UtxoWithBalance(receiveUtxo, poolDenomination);
    return postmixHandler.computeNextPremixHandler(utxoWithBalance);
  }

  protected MixSuccess computeMixSuccess() {
    return new MixSuccess(this.receiveAddress, this.receiveUtxo);
  }

  //

  private void checkFees(long inputValue, long outputValue) throws NotifiableException {
    long fees = inputValue - outputValue;

    if (liquidity && fees > 0) {
      throw new NotifiableException("Should not pay fees as a liquidity");
    }
    if (fees > MAX_ACCEPTABLE_FEES) {
      log.error(
          "Fees abnormally abnormally: fees="
              + fees
              + ", MAX_ACCEPTABLE_FEES="
              + MAX_ACCEPTABLE_FEES);
      throw new NotifiableException("Fees abnormally high");
    }
  }

  private void checkUtxoBalance(long mustMixBalanceMin, long mustMixBalanceMax)
      throws NotifiableException {
    long premixBalanceMin =
        WhirlpoolProtocol.computePremixBalanceMin(poolDenomination, mustMixBalanceMin, liquidity);
    long premixBalanceMax =
        WhirlpoolProtocol.computePremixBalanceMax(poolDenomination, mustMixBalanceMax, liquidity);

    long utxoBalance = premixHandler.getUtxo().getBalance();
    if (utxoBalance < premixBalanceMin) {
      throw new NotifiableException(
          "Too low utxo-balance="
              + utxoBalance
              + ". (expected: "
              + premixBalanceMin
              + " <= utxo-balance <= "
              + premixBalanceMax
              + ")");
    }

    if (utxoBalance > premixBalanceMax) {
      throw new NotifiableException(
          "Too high utxo-balance="
              + utxoBalance
              + ". (expected: "
              + premixBalanceMin
              + " <= utxo-balance <= "
              + premixBalanceMax
              + ")");
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
    receiveUtxo = new Utxo(tx.getHashAsString(), outputIndex);

    // verify my input
    UtxoWithBalance utxo = premixHandler.getUtxo();
    Integer inputIndex = TxUtil.getInstance().findInputIndex(tx, utxo.getHash(), utxo.getIndex());
    if (outputIndex == null) {
      throw new Exception("Input not found in tx");
    }

    // check fees again
    long inputValue = utxo.getBalance(); // tx.getInput(inputIndex).getValue().getValue(); is null
    long outputValue = tx.getOutput(outputIndex).getValue().getValue();
    checkFees(inputValue, outputValue);

    // as many inputs as outputs
    if (tx.getInputs().size() != tx.getOutputs().size()) {
      log.error(
          "inputs.size = " + tx.getInputs().size() + ", outputs.size=" + tx.getOutputs().size());
      throw new Exception("Inputs size vs outputs size mismatch");
    }

    // each output value should be denomination
    for (TransactionOutput output : tx.getOutputs()) {
      if (output.getValue().getValue() != poolDenomination) {
        log.error(
            "outputValue=" + output.getValue().getValue() + ", denomination=" + poolDenomination);
        throw new Exception("Output value mismatch");
      }
    }
    return inputIndex;
  }

  private String computeInputsHash(List<TransactionInput> inputs) {
    List<Utxo> utxos = new ArrayList<Utxo>();
    for (TransactionInput input : inputs) {
      Utxo utxo =
          new Utxo(input.getOutpoint().getHash().toString(), input.getOutpoint().getIndex());
      utxos.add(utxo);
    }
    return WhirlpoolProtocol.computeInputsHash(utxos);
  }

  private boolean throwProtocolException() throws Exception {
    String message =
        "Protocol exception: "
            + " registeredInput="
            + registeredInput
            + " confirmedInput="
            + confirmedInput
            + ", confirmedInputResponse="
            + confirmedInputResponse
            + ", registeredOutput"
            + registeredOutput
            + ", revealedOutput"
            + revealedOutput
            + ", signed="
            + signed;
    throw new ProtocolException(message);
  }
}
