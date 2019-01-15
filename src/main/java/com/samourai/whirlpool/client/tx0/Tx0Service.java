package com.samourai.whirlpool.client.tx0;

import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.client.utils.FeeUtils;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tx0Service {
  private Logger log = LoggerFactory.getLogger(Tx0Service.class);
  public static final int NB_PREMIX_MAX = 100;

  private final Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  private final WhirlpoolFee whirlpoolFee = WhirlpoolFee.getInstance();

  private NetworkParameters params;
  private String feeXpub;
  private long feeValue;

  public Tx0Service(NetworkParameters params, String feeXpub, long feeValue) {
    this.params = params;
    this.feeXpub = feeXpub;
    this.feeValue = feeValue;
  }

  public long computeSpendFromBalanceMin(Pool pool, int feeSatPerByte, int nbOutputsMin) {
    long destinationValue = computePremixValue(pool, feeSatPerByte);
    final long spendFromBalanceMin = nbOutputsMin * (destinationValue + feeValue);
    return spendFromBalanceMin;
  }

  private long computePremixValue(Pool pool, int feeSatPerByte) {
    // compute minerFeePerMustmix
    long txFeesEstimate =
        FeeUtils.computeMinerFee(
            pool.getMixAnonymitySet(), pool.getMixAnonymitySet(), feeSatPerByte);
    long minerFeePerMustmix = txFeesEstimate / pool.getMixAnonymitySet();
    long destinationValue = pool.getDenomination() + minerFeePerMustmix;

    // make sure destinationValue is acceptable for pool
    long balanceMin =
        WhirlpoolProtocol.computeInputBalanceMin(
            pool.getDenomination(), false, pool.getMinerFeeMin());
    long balanceMax =
        WhirlpoolProtocol.computeInputBalanceMax(
            pool.getDenomination(), false, pool.getMinerFeeMax());
    destinationValue = Math.min(destinationValue, balanceMax);
    destinationValue = Math.max(destinationValue, balanceMin);

    if (log.isDebugEnabled()) {
      log.debug(
          "destinationValue="
              + destinationValue
              + ", minerFeePerMustmix="
              + minerFeePerMustmix
              + ", txFeesEstimate="
              + txFeesEstimate);
    }
    return destinationValue;
  }

  private int computePremixNb(long premixValue, TransactionOutPoint depositSpendFrom) {
    int nbPremix = (int) Math.ceil(depositSpendFrom.getValue().getValue() / premixValue);
    return nbPremix;
  }

  public Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      int feeSatPerByte,
      IIndexHandler feeIndexHandler,
      Pools pools,
      Pool pool)
      throws Exception {
    return tx0(
        spendFromPrivKey,
        depositSpendFrom,
        depositWallet,
        premixWallet,
        feeSatPerByte,
        feeIndexHandler,
        pools,
        pool,
        NB_PREMIX_MAX);
  }

  public Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      int feeSatPerByte,
      IIndexHandler feeIndexHandler,
      Pools pools,
      Pool pool,
      int nbPremix)
      throws Exception {

    // cap nbPremix with UTXO balance
    long premixValue = computePremixValue(pool, feeSatPerByte);
    int nbPremixPossible = computePremixNb(premixValue, depositSpendFrom);
    nbPremix = Math.min(nbPremix, nbPremixPossible);

    // cap nbPremix with UTXO NB_PREMIX_MAX
    nbPremix = Math.min(NB_PREMIX_MAX, nbPremix);
    return tx0(
        spendFromPrivKey,
        depositSpendFrom,
        depositWallet,
        premixWallet,
        feeSatPerByte,
        feeIndexHandler,
        pools.getFeePaymentCode(),
        pools.getFeePayload(),
        premixValue,
        nbPremix);
  }

  public Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      long feeSatPerByte,
      IIndexHandler feeIndexHandler,
      String feePaymentCode,
      byte[] feePayload,
      long premixValue,
      int premixNb)
      throws Exception {

    long spendFromBalance = depositSpendFrom.getValue().getValue();

    //
    // tx0
    //

    //
    // make tx:
    // 5 spendTo outputs
    // SW fee
    // change
    // OP_RETURN
    //
    List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
    Transaction tx = new Transaction(params);

    //
    // premix outputs
    //
    for (int j = 0; j < premixNb; j++) {
      // send to PREMIX
      HD_Address toAddress = premixWallet.getNextAddress();
      String toAddressBech32 = bech32Util.toBech32(toAddress, params);
      if (log.isDebugEnabled()) {
        log.debug(
            "Tx0 out (premix): address="
                + toAddressBech32
                + ", path="
                + toAddress.toJSON().get("path")
                + " ("
                + premixValue
                + " sats)");
      }

      TransactionOutput txOutSpend =
          bech32Util.getTransactionOutput(toAddressBech32, premixValue, params);
      outputs.add(txOutSpend);
    }

    int feeIndice = (feePayload != null ? 0 : feeIndexHandler.getAndIncrement());
    byte[] opReturnValue =
        whirlpoolFee.encode(
            feeIndice, feePayload, feePaymentCode, params, spendFromPrivKey, depositSpendFrom);

    // fee estimation: n outputs + change + fee + OP_RETURN
    long totalBytes =
        FeeUtils.estimateTxBytes(1, premixNb + 2) + FeeUtils.estimateOpReturnBytes(opReturnValue);
    if (log.isDebugEnabled()) {
      log.debug("tx size estimation final: " + totalBytes + "b");
    }
    long tx0MinerFee = FeeUtils.computeMinerFee(totalBytes, feeSatPerByte);
    long changeValue = spendFromBalance - (premixValue * premixNb) - feeValue - tx0MinerFee;

    if (changeValue > 0) {
      //
      // 1 change output
      //
      HD_Address changeAddress = depositWallet.getNextAddress();
      String changeAddressBech32 = bech32Util.toBech32(changeAddress, params);
      TransactionOutput txChange =
          bech32Util.getTransactionOutput(changeAddressBech32, changeValue, params);
      outputs.add(txChange);
      if (log.isDebugEnabled()) {
        log.debug(
            "Tx0 out (change): address="
                + changeAddressBech32
                + ", path="
                + changeAddress.toJSON().get("path")
                + " ("
                + changeValue
                + " sats)");
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("Tx0: spending whole utx0, no change");
      }
    }

    // samourai fee
    String feeAddressBech32;
    if (feePayload == null) {
      // pay to xpub
      feeAddressBech32 = computeFeeAddress(feeXpub, feeIndice);
      if (log.isDebugEnabled()) {
        log.debug("Tx0 out (fee->xpub): address=" + feeAddressBech32 + " (" + feeValue + " sats)");
      }
    } else {
      // pay to deposit
      feeAddressBech32 = bech32Util.toBech32(depositWallet.getNextAddress(), params);
      if (log.isDebugEnabled()) {
        log.debug(
            "Tx0 out (fee->deposit): address=" + feeAddressBech32 + " (" + feeValue + " sats)");
      }
    }
    TransactionOutput txSWFee = bech32Util.getTransactionOutput(feeAddressBech32, feeValue, params);
    outputs.add(txSWFee);

    // add OP_RETURN output
    Script op_returnOutputScript =
        new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(opReturnValue).build();
    TransactionOutput txFeeOutput =
        new TransactionOutput(params, null, Coin.valueOf(0L), op_returnOutputScript.getProgram());
    outputs.add(txFeeOutput);
    if (log.isDebugEnabled()) {
      log.debug(
          "Tx0 out (OP_RETURN): feeIndice="
              + feeIndice
              + ", feePayloadHex="
              + (feePayload != null ? Hex.toHexString(feePayload) : "null"));
    }

    // all outputs
    Collections.sort(outputs, new BIP69OutputComparator());
    for (TransactionOutput to : outputs) {
      tx.addOutput(to);
    }

    // input
    ECKey spendFromKey = ECKey.fromPrivate(spendFromPrivKey);

    final Script segwitPubkeyScript = ScriptBuilder.createP2WPKHOutputScript(spendFromKey);
    tx.addSignedInput(depositSpendFrom, segwitPubkeyScript, spendFromKey);
    if (log.isDebugEnabled()) {
      log.debug(
          "Tx0 in: utxo="
              + depositSpendFrom
              + " ("
              + depositSpendFrom.getValue().getValue()
              + " sats)");
      log.debug("Tx0 fee: " + tx0MinerFee + " sats");
    }

    final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
    final String strTxHash = tx.getHashAsString();

    tx.verify();
    // System.out.println(tx);
    if (log.isDebugEnabled()) {
      log.debug("Tx0 hash: " + strTxHash);
      log.debug("Tx0 hex: " + hexTx);
      long feePrice = tx0MinerFee / tx.getVirtualTransactionSize();
      log.debug("Tx0 size: " + tx.getVirtualTransactionSize() + "b, feePrice=" + feePrice + "s/b");
    }

    List<Utxo> premixUtxos = new ArrayList<Utxo>();
    for (TransactionOutput to : tx.getOutputs()) {
      Utxo utxo = new Utxo(strTxHash, to.getIndex());
      premixUtxos.add(utxo);
    }
    return new Tx0(tx, premixUtxos);
  }

  private String computeFeeAddress(String xpubFee, int feeIndice) {
    DeterministicKey mKey = FormatsUtilGeneric.getInstance().createMasterPubKeyFromXPub(xpubFee);
    DeterministicKey cKey =
        HDKeyDerivation.deriveChildKey(
            mKey, new ChildNumber(0, false)); // assume external/receive chain
    DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(feeIndice, false));
    ECKey feePubkey = ECKey.fromPublicOnly(adk.getPubKey());
    String feeAddressBech32 = bech32Util.toBech32(feePubkey.getPubKey(), params);
    return feeAddressBech32;
  }
}
