package com.samourai.whirlpool.client.utils;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.http.client.HttpException;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import java.lang.invoke.MethodHandles;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUtils {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static Integer findTxOutputIndex(
      String outputAddressBech32, Transaction tx, NetworkParameters params) {
    try {
      byte[] expectedScriptBytes =
          Bech32UtilGeneric.getInstance().computeScriptPubKey(outputAddressBech32, params);
      for (TransactionOutput output : tx.getOutputs()) {
        if (Arrays.equals(output.getScriptBytes(), expectedScriptBytes)) {
          return output.getIndex();
        }
      }
    } catch (Exception e) {
      log.error("findTxOutput failed", e);
    }
    return null;
  }

  public static Integer findTxInputIndex(String utxoHash, long utxoIdx, Transaction tx) {
    for (int index = 0; index < tx.getInputs().size(); index++) {
      TransactionInput input = tx.getInput(index);
      TransactionOutPoint transactionOutPoint = input.getOutpoint();
      if (transactionOutPoint.getHash().toString().equals(utxoHash)
          && transactionOutPoint.getIndex() == utxoIdx) {
        return index;
      }
    }
    return null;
  }

  public static String generateUniqueString() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static String[] witnessSerialize64(TransactionWitness witness) {
    String[] serialized = new String[witness.getPushCount()];
    for (int i = 0; i < witness.getPushCount(); i++) {
      serialized[i] = encodeBase64(witness.getPush(i));
    }
    return serialized;
  }

  public static RSAKeyParameters publicKeyUnserialize(byte[] publicKeySerialized) throws Exception {
    RSAPublicKey rsaPublicKey =
        (RSAPublicKey)
            KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKeySerialized));
    return new RSAKeyParameters(false, rsaPublicKey.getModulus(), rsaPublicKey.getPublicExponent());
  }

  public static void signSegwitInput(
      Transaction tx, int inputIdx, ECKey ecKey, long spendAmount, NetworkParameters params)
      throws Exception {
    final SegwitAddress segwitAddress = new SegwitAddress(ecKey, params);
    final Script redeemScript = segwitAddress.segWitRedeemScript();
    final Script scriptCode = redeemScript.scriptCode();

    TransactionSignature sig =
        tx.calculateWitnessSignature(
            inputIdx, ecKey, scriptCode, Coin.valueOf(spendAmount), Transaction.SigHash.ALL, false);
    final TransactionWitness witness = new TransactionWitness(2);
    witness.setPush(0, sig.encodeToBitcoin());
    witness.setPush(1, ecKey.getPubKey());
    tx.setWitness(inputIdx, witness);
  }

  public static String toJsonString(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (Exception e) {
      log.error("", e);
    }
    return null;
  }

  public static <T> T fromJson(String json, Class<T> type) throws Exception {
    return objectMapper.readValue(json, type);
  }

  public static Logger prefixLogger(Logger log, String logPrefix) {
    Level level = ((ch.qos.logback.classic.Logger) log).getEffectiveLevel();
    Logger newLog =
        LoggerFactory.getLogger(MethodHandles.lookup().lookupClass() + "[" + logPrefix + "]");
    ((ch.qos.logback.classic.Logger) newLog).setLevel(level);
    return newLog;
  }

  private static String parseRestErrorMessage(String responseBody) {
    try {
      RestErrorResponse restErrorResponse =
          ClientUtils.fromJson(responseBody, RestErrorResponse.class);
      return restErrorResponse.message;
    } catch (Exception e) {
      return null;
    }
  }

  public static String parseRestErrorMessage(HttpException e) {
    String responseBody = e.getResponseBody();
    if (responseBody == null) {
      return null;
    }
    return parseRestErrorMessage(responseBody);
  }

  public static byte[] decodeBase64(String base64) {
    return Base64.decodeBase64(base64);
  }

  public static String encodeBase64(byte[] data) {
    return Base64.encodeBase64String(data);
  }
}
