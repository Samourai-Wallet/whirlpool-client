package com.samourai.whirlpool.client.utils;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.api.backend.beans.UnspentResponse.UnspentOutput;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.CallbackWithArg;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoConfig;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoState;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.bitcoinj.core.*;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientUtils {
  private static final Logger log = LoggerFactory.getLogger(ClientUtils.class);
  private static final SecureRandom secureRandom = new SecureRandom();

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

  public static void logWhirlpoolUtxos(Collection<WhirlpoolUtxo> utxos, int mixsTargetMin) {
    String lineFormat = "| %10s | %8s | %68s | %14s | %12s | %14s | %8s | %8s |\n";
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            lineFormat,
            "BALANCE",
            "CONFIRMS",
            "UTXO",
            "PATH",
            "STATUS",
            "MIXABLE",
            "POOL",
            "MIXS"));
    sb.append(String.format(lineFormat, "(btc)", "", "", "", "", "", "", ""));
    Iterator var3 = utxos.iterator();

    while (var3.hasNext()) {
      WhirlpoolUtxo whirlpoolUtxo = (WhirlpoolUtxo) var3.next();
      WhirlpoolUtxoConfig utxoConfig = whirlpoolUtxo.getUtxoConfig();
      WhirlpoolUtxoState utxoState = whirlpoolUtxo.getUtxoState();
      UnspentOutput o = whirlpoolUtxo.getUtxo();
      String utxo = o.tx_hash + ":" + o.tx_output_n;
      String mixableStatusName =
          utxoState.getMixableStatus() != null ? utxoState.getMixableStatus().name() : "-";
      int mixsTargetOrDefault = utxoConfig.getMixsTargetOrDefault(mixsTargetMin);
      sb.append(
          String.format(
              lineFormat,
              ClientUtils.satToBtc(o.value),
              o.confirmations,
              utxo,
              o.getPath(),
              utxoState.getStatus().name(),
              mixableStatusName,
              utxoConfig.getPoolId() != null ? utxoConfig.getPoolId() : "-",
              utxoConfig.getMixsDone()
                  + "/"
                  + (mixsTargetOrDefault == WhirlpoolUtxoConfig.MIXS_TARGET_UNLIMITED
                      ? "∞"
                      : mixsTargetOrDefault)));
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

  public static int random(int minInclusive, int maxInclusive) {
    return secureRandom.nextInt(maxInclusive + 1 - minInclusive) + minInclusive;
  }

  public static void safeWrite(File file, CallbackWithArg<File> callback) throws Exception {
    if (!file.exists()) {
      file.createNewFile();
    }
    FileLock fileLock = lockFile(file);

    File tempFile = null;
    try {
      tempFile = File.createTempFile(file.getName(), "");

      // write to temp file
      callback.apply(tempFile);

      // delete existing file if any
      if (file.exists()) {
        if (!file.delete()) {
          throw new NotifiableException("Cannot delete file: " + file.getAbsolutePath());
        }
      }
      // then rename
      if (!tempFile.renameTo(file)) {
        throw new NotifiableException(
            "File rename failed: " + tempFile.getAbsolutePath() + " -> " + file.getAbsolutePath());
      }
    } catch (Exception e) {
      log.error(
          "safeWrite failed for "
              + (tempFile != null ? tempFile.getAbsolutePath() : "null")
              + " ->"
              + file.getAbsolutePath());
      throw e;
    } finally {
      unlockFile(fileLock);
    }
  }

  public static void safeWriteValue(final ObjectMapper mapper, final Object value, final File file)
      throws Exception {
    CallbackWithArg<File> callback =
        new CallbackWithArg<File>() {
          @Override
          public void apply(File tempFile) throws Exception {
            mapper.writeValue(tempFile, value);
          }
        };
    safeWrite(file, callback);
  }

  public static FileLock lockFile(File f) throws Exception {
    return lockFile(
        f,
        "Cannot lock file "
            + f.getAbsolutePath()
            + ". Make sure no other Whirlpool instance is running in same directory.");
  }

  public static FileLock lockFile(File f, String errorMsg) throws Exception {
    FileChannel channel = new RandomAccessFile(f, "rw").getChannel();
    FileLock fileLock = channel.tryLock(); // exclusive lock
    if (fileLock == null) {
      throw new NotifiableException(errorMsg);
    }
    return fileLock; // success
  }

  public static void unlockFile(FileLock fileLock) throws Exception {
    fileLock.release();
    fileLock.channel().close();
  }

  public static void setLogLevel(Level mainLevel, Level subLevel) {
    LogbackUtils.setLogLevel("com.samourai", mainLevel.toString());

    LogbackUtils.setLogLevel("com.samourai.whirlpool.client", subLevel.toString());
    LogbackUtils.setLogLevel("com.samourai.stomp.client", subLevel.toString());
    LogbackUtils.setLogLevel("com.samourai.wallet.util.FeeUtil", subLevel.toString());

    LogbackUtils.setLogLevel("com.samourai.whirlpool.client.wallet", mainLevel.toString());
    LogbackUtils.setLogLevel(
        "com.samourai.whirlpool.client.wallet.orchestrator", mainLevel.toString());

    // skip noisy logs
    LogbackUtils.setLogLevel("org.bitcoinj", org.slf4j.event.Level.ERROR.toString());
    LogbackUtils.setLogLevel(
        "org.bitcoin", org.slf4j.event.Level.WARN.toString()); // "no wallycore"
  }
}
