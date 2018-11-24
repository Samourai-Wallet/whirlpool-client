package com.samourai.whirlpool.client.utils;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.SecretPointJava;
import com.samourai.wallet.bip47.rpc.secretPoint.ISecretPoint;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.math.BigInteger;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutPoint;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

public class ClientCryptoService {

  public ClientCryptoService() {}

  public RSABlindingParameters computeBlindingParams(RSAKeyParameters publicKey) {
    // Generate a blinding factor using the notary's public key.
    RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
    blindingFactorGenerator.init(publicKey);

    BigInteger blindingFactor = blindingFactorGenerator.generateBlindingFactor();
    return new RSABlindingParameters(publicKey, blindingFactor);
  }

  public byte[] blind(String toBlind, RSABlindingParameters blindingParams) throws CryptoException {
    return blind(toBlind.getBytes(), blindingParams);
  }

  public byte[] blind(byte[] toBlind, RSABlindingParameters blindingParams) throws CryptoException {
    PSSSigner blinder = new PSSSigner(new RSABlindingEngine(), new SHA256Digest(), 32);
    blinder.init(true, blindingParams);
    blinder.update(toBlind, 0, toBlind.length);

    byte[] blindedData = blinder.generateSignature();
    return blindedData;
  }

  public byte[] unblind(byte[] signedBlindedOutput, RSABlindingParameters blindingParams) {
    RSABlindingEngine unblinder = new RSABlindingEngine();
    unblinder.init(false, blindingParams);

    byte[] unblindedData =
        unblinder.processBlock(signedBlindedOutput, 0, signedBlindedOutput.length);
    return unblindedData;
  }

  public byte[] xorMask(
      byte[] dataToMask,
      PaymentCode paymentCode,
      NetworkParameters params,
      ECKey input0PrivKey,
      TransactionOutPoint input0OutPoint)
      throws Exception {
    HD_Address notifAddressCli = paymentCode.notificationAddress(params);
    ISecretPoint secretPointMask =
        new SecretPointJava(input0PrivKey.getPrivKeyBytes(), notifAddressCli.getPubKey());
    byte[] dataMasked = PaymentCode.xorMask(dataToMask, secretPointMask, input0OutPoint);
    return dataMasked;
  }

  public byte[] xorMaskInteger(
      int dataToMask,
      PaymentCode paymentCode,
      NetworkParameters params,
      ECKey input0PrivKey,
      TransactionOutPoint input0OutPoint)
      throws Exception {
    byte[] dataToMaskBytes = WhirlpoolProtocol.getWhirlpoolFee().encode(dataToMask);
    if (dataToMaskBytes == null) {
      return null;
    }
    return xorMask(dataToMaskBytes, paymentCode, params, input0PrivKey, input0OutPoint);
  }
}
