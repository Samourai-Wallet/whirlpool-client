package com.samourai.whirlpool.client.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeeUtils {
  private static final Logger log = LoggerFactory.getLogger(FeeUtils.class);

  private static final long TX_BYTES_PER_INPUT = 70;
  private static final long TX_BYTES_PER_OUTPUT = 31;
  private static final long TX_BYTES_PER_OPRETURN = 20;
  private static final long MIN_RELAY_FEE_PER_BYTE = 1;

  public static long estimateTxBytes(int nbInputs, int nbOutputs) {
    long bytes = TX_BYTES_PER_INPUT * nbInputs + TX_BYTES_PER_OUTPUT * nbOutputs;
    if (log.isDebugEnabled()) {
      log.debug("tx size estimation: " + bytes + "b (" + nbInputs + " ins, " + nbOutputs + "outs)");
    }

    // TODO
    if (nbInputs == 1 && nbOutputs == 1) {
      return 191;
    }
    return bytes;
  }

  public static long estimateOpReturnBytes(int opReturnValueLength) {
    long bytes = TX_BYTES_PER_OPRETURN + opReturnValueLength;
    if (log.isDebugEnabled()) {
      log.debug(
          "OP_RETURN size estimation: "
              + bytes
              + "b ("
              + opReturnValueLength
              + " + "
              + TX_BYTES_PER_OPRETURN
              + ")");
    }
    return bytes;
  }

  public static long computeMinerFee(int nbInputs, int nbOutputs, long feePerByte) {
    long bytes = estimateTxBytes(nbInputs, nbOutputs);
    return computeMinerFee(bytes, feePerByte);
  }

  public static long computeMinerFee(long bytes, long feePerByte) {
    if (feePerByte < MIN_RELAY_FEE_PER_BYTE) {
      if (log.isDebugEnabled()) {
        log.debug(
            "minerFee = " + feePerByte + "s/b => MIN_RELAY_FEE " + MIN_RELAY_FEE_PER_BYTE + "s/b");
      }
      feePerByte = MIN_RELAY_FEE_PER_BYTE;
    }
    long minerFee = bytes * feePerByte;
    if (log.isDebugEnabled()) {
      log.debug("minerFee = " + minerFee + " (" + bytes + "b, " + feePerByte + "s/b)");
    }
    return minerFee;
  }
}
