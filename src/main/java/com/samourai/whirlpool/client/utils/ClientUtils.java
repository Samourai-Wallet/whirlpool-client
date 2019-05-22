package com.samourai.whirlpool.client.utils;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUtils {
  private static final Logger log = LoggerFactory.getLogger(ClientUtils.class);

  private static final int SLEEP_REFRESH_UTXOS_TESTNET = 20000;
  private static final int SLEEP_REFRESH_UTXOS_MAINNET = 10000;
  public static final String USER_AGENT = "whirlpool-client/" + WhirlpoolProtocol.PROTOCOL_VERSION;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static void setupEnv() {
    // prevent user-agent tracking
    System.setProperty("http.agent", USER_AGENT);
  }

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

  public static String[] witnessSerialize64(TransactionWitness witness) {
    String[] serialized = new String[witness.getPushCount()];
    for (int i = 0; i < witness.getPushCount(); i++) {
      serialized[i] = WhirlpoolProtocol.encodeBytes(witness.getPush(i));
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
    Logger newLog = LoggerFactory.getLogger(log.getName() + "[" + logPrefix + "]");
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

  public static void logUtxos(Collection<UnspentOutput> utxos) {
    String lineFormat = "| %10s | %8s | %68s | %45s | %14s |\n";
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(lineFormat, "BALANCE", "CONFIRMS", "UTXO", "ADDRESS", "PATH"));
    sb.append(String.format(lineFormat, "(btc)", "", "", "", ""));
    for (UnspentResponse.UnspentOutput o : utxos) {
      String utxo = o.tx_hash + ":" + o.tx_output_n;
      sb.append(
          String.format(lineFormat, satToBtc(o.value), o.confirmations, utxo, o.addr, o.getPath()));
    }
    log.info("\n" + sb.toString());
  }

  public static void logWhirlpoolUtxos(Collection<WhirlpoolUtxo> utxos) {
    String lineFormat = "| %10s | %8s | %68s | %45s | %14s | %12s | %12s | %6s |\n";
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            lineFormat,
            "BALANCE",
            "CONFIRMS",
            "UTXO",
            "ADDRESS",
            "PATH",
            "STATUS",
            "MIXABLE",
            "POOL",
            "MIXS"));
    sb.append(String.format(lineFormat, "(btc)", "", "", "", "", "", "", "", ""));
    Iterator var3 = utxos.iterator();

    while (var3.hasNext()) {
      WhirlpoolUtxo whirlpoolUtxo = (WhirlpoolUtxo) var3.next();
      WhirlpoolUtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfig();
      UnspentOutput o = whirlpoolUtxo.getUtxo();
      String utxo = o.tx_hash + ":" + o.tx_output_n;
      String mixableStatusName =
          whirlpoolUtxo.getMixableStatus() != null ? whirlpoolUtxo.getMixableStatus().name() : "-";
      sb.append(
          String.format(
              lineFormat,
              ClientUtils.satToBtc(o.value),
              o.confirmations,
              utxo,
              o.addr,
              o.getPath(),
              whirlpoolUtxo.getStatus().name(),
              mixableStatusName,
              utxoConfig.getPoolId() != null ? utxoConfig.getPoolId() : "-",
              utxoConfig.getMixsDone() + "/" + utxoConfig.getMixsTarget()));
    }

    log.info("\n" + sb.toString());
  }

  public static double satToBtc(long sat) {
    return sat / 100000000.0;
  }

  public static String utxoToKey(UnspentOutput unspentOutput) {
    return unspentOutput.tx_hash + ':' + unspentOutput.tx_output_n;
  }

  public static String utxoToKey(String utxoHash, int utxoIndex) {
    return utxoHash + ':' + utxoIndex;
  }

  public static String getTxHex(Transaction tx) {
    String txHex = org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize());
    return txHex;
  }

  public static void sleepRefreshUtxos(NetworkParameters params) {
    if (log.isDebugEnabled()) {
      log.debug("Refreshing utxos...");
    }
    boolean isTestnet = FormatsUtilGeneric.getInstance().isTestNet(params);
    int sleepDelay = isTestnet ? SLEEP_REFRESH_UTXOS_TESTNET : SLEEP_REFRESH_UTXOS_MAINNET;
    try {
      Thread.sleep(sleepDelay);
    } catch (InterruptedException e) {
    }
  }

  public static String sha256Hash(String str) {
    return sha256Hash(str.getBytes());
  }

  public static String sha256Hash(byte[] bytes) {
    return Sha256Hash.wrap(Sha256Hash.hash(bytes)).toString();
  }

  public static String maskString(String value) {
    return maskString(value, 3);
  }

  private static String maskString(String value, int startEnd) {
    if (value.length() <= startEnd) {
      return value;
    }
    return value.substring(0, Math.min(startEnd, value.length()))
        + "..."
        + value.substring(Math.max(0, value.length() - startEnd), value.length());
  }
}
