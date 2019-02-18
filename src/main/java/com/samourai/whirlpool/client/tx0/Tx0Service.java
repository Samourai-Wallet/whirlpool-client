package com.samourai.whirlpool.client.tx0;

import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.client.indexHandler.IIndexHandler;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FeeUtil;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Pools;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
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
  protected static final int NB_PREMIX_MAX = 100;

  private final Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  private final WhirlpoolFee whirlpoolFee = WhirlpoolFee.getInstance();
  private final FeeUtil feeUtil = FeeUtil.getInstance();

  private NetworkParameters params;
  private String feeXpub;
  private long feeValue;

  public Tx0Service(NetworkParameters params, String feeXpub, long feeValue) {
    this.params = params;
    this.feeXpub = feeXpub;
    this.feeValue = feeValue;
  }

  private long computePremixValue(Pool pool, int feeSatPerByte) {
    // compute minerFeePerMustmix
    long txFeesEstimate =
        feeUtil.estimatedFeeSegwit(
            0, 0, pool.getMixAnonymitySet(), pool.getMixAnonymitySet(), 0, feeSatPerByte);
    long minerFeePerMustmix = txFeesEstimate / pool.getMixAnonymitySet();
    long premixValue = pool.getDenomination() + minerFeePerMustmix;

    // make sure destinationValue is acceptable for pool
    long balanceMin =
        WhirlpoolProtocol.computeInputBalanceMin(
            pool.getDenomination(), false, pool.getMinerFeeMin());
    long balanceMax =
        WhirlpoolProtocol.computeInputBalanceMax(
            pool.getDenomination(), false, pool.getMinerFeeMax());
    premixValue = Math.min(premixValue, balanceMax);
    premixValue = Math.max(premixValue, balanceMin);

    if (log.isDebugEnabled()) {
      log.debug(
          "premixValue="
              + premixValue
              + ", minerFeePerMustmix="
              + minerFeePerMustmix
              + ", txFeesEstimate="
              + txFeesEstimate
              + " for poolId="
              + pool.getPoolId());
    }
    return premixValue;
  }

  private int computeNbPremixMax(
      long premixValue, TransactionOutPoint depositSpendFrom, long feeSatPerByte) throws Exception {
    long spendFromBalance = depositSpendFrom.getValue().getValue();

    // compute nbPremix ignoring TX0 fee
    int nbPremixInitial = (int) Math.ceil(spendFromBalance / premixValue);

    // compute nbPremix with TX0 fee
    int nbPremix = nbPremixInitial;
    while (true) {
      // estimate TX0 fee for nbPremix
      long tx0MinerFee = computeTx0MinerFee(nbPremix, feeSatPerByte, depositSpendFrom);
      long spendValue = computeTx0SpendValue(premixValue, nbPremix, tx0MinerFee);
      if (log.isDebugEnabled()) {
        log.debug(
            "computeNbPremixMax: nbPremix="
                + nbPremix
                + " => spendValue="
                + spendValue
                + ", tx0MinerFee="
                + tx0MinerFee
                + ", spendFromBalance="
                + spendFromBalance
                + ", nbPremixInitial="
                + nbPremixInitial);
      }
      if (spendFromBalance < spendValue) {
        // if UTXO balance is insufficient, try with less nbPremix
        nbPremix--;
      } else {
        // nbPremix found
        break;
      }
    }
    // no negative value
    if (nbPremix < 0) {
      nbPremix = 0;
    }
    return nbPremix;
  }

  private long computeTx0MinerFee(int nbPremix, long feeSatPerByte, TransactionOutPoint spendFrom) {
    int nbOutputsNonOpReturn = nbPremix + 2; // outputs + change + fee
    // compute fee for worst input possible => P2PKH
    long tx0MinerFee = feeUtil.estimatedFeeSegwit(1, 0, 0, nbOutputsNonOpReturn, 1, feeSatPerByte);

    if (log.isDebugEnabled()) {
      log.debug(
          "tx0 minerFee: "
              + tx0MinerFee
              + "sats, totalBytes="
              + "b for nbPremix="
              + nbPremix
              + ", feeSatPerByte="
              + feeSatPerByte);
    }
    return tx0MinerFee;
  }

  private long computeTx0SpendValue(long premixValue, long nbPremix, long tx0MinerFee) {
    long changeValue = (premixValue * nbPremix) + feeValue + tx0MinerFee;
    return changeValue;
  }

  public long computeSpendFromBalanceMin(Pool pool, int feeSatPerByte, int nbPremix) {
    long premixValue = computePremixValue(pool, feeSatPerByte);
    long tx0MinerFee = computeTx0MinerFee(nbPremix, feeSatPerByte, null);
    long spendValue = computeTx0SpendValue(premixValue, nbPremix, tx0MinerFee);
    return spendValue;
  }

  private String computeFeeAddressDestination(
      byte[] feePayload, int feeIndice, Bip84Wallet depositWallet) {
    String feeAddressBech32;
    if (feePayload == null) {
      // pay to xpub
      feeAddressBech32 = computeFeeAddressToXpub(feeXpub, feeIndice);
      if (log.isDebugEnabled()) {
        log.debug("feeAddressDestination: xpub");
      }
    } else {
      // pay to deposit
      feeAddressBech32 = bech32Util.toBech32(depositWallet.getNextChangeAddress(), params);
      if (log.isDebugEnabled()) {
        log.debug("feeAddressDestination: deposit");
      }
    }
    return feeAddressBech32;
  }

  /** Generate maximum possible premixes outputs. */
  public Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      IIndexHandler feeIndexHandler,
      int feeSatPerByte,
      Pools pools,
      Pool pool)
      throws Exception {
    return tx0(
        spendFromPrivKey,
        depositSpendFrom,
        depositWallet,
        premixWallet,
        feeIndexHandler,
        feeSatPerByte,
        pools,
        pool,
        NB_PREMIX_MAX);
  }

  /** Generate nbPremixPreferred premixes outputs max. */
  public Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      IIndexHandler feeIndexHandler,
      int feeSatPerByte,
      Pools pools,
      Pool pool,
      int nbPremixPreferred)
      throws Exception {

    // compute premixValue for pool
    long premixValue = computePremixValue(pool, feeSatPerByte);
    return tx0(
        spendFromPrivKey,
        depositSpendFrom,
        depositWallet,
        premixWallet,
        feeIndexHandler,
        feeSatPerByte,
        nbPremixPreferred,
        premixValue,
        pools.getFeePaymentCode(),
        pools.getFeePayload());
  }

  protected Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      IIndexHandler feeIndexHandler,
      long feeSatPerByte,
      int nbPremixPreferred,
      long premixValue,
      String feePaymentCode,
      byte[] feePayload)
      throws Exception {

    // compute opReturnValue for feePaymentCode and feePayload
    int feeIndice = (feePayload != null ? 0 : feeIndexHandler.getAndIncrement());
    byte[] opReturnValue =
        whirlpoolFee.encode(
            feeIndice, feePayload, feePaymentCode, params, spendFromPrivKey, depositSpendFrom);
    if (log.isDebugEnabled()) {
      log.debug(
          "computing opReturnValue for feeIndice="
              + feeIndice
              + ", feePayloadHex="
              + (feePayload != null ? Hex.toHexString(feePayload) : "null"));
    }

    String feeAddressBech32 = computeFeeAddressDestination(feePayload, feeIndice, depositWallet);
    return tx0(
        spendFromPrivKey,
        depositSpendFrom,
        depositWallet,
        premixWallet,
        feeSatPerByte,
        nbPremixPreferred,
        premixValue,
        opReturnValue,
        feeAddressBech32);
  }

  protected Tx0 tx0(
      byte[] spendFromPrivKey,
      TransactionOutPoint depositSpendFrom,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      long feeSatPerByte,
      int nbPremixPreferred,
      long premixValue,
      byte[] opReturnValue,
      String feeAddressBech32)
      throws Exception {

    long spendFromBalance = depositSpendFrom.getValue().getValue();

    // compute nbPremix
    int nbPremix =
        computeNbPremixMax(
            premixValue, depositSpendFrom, feeSatPerByte); // cap with balance and tx0 minerFee
    nbPremix = Math.min(nbPremixPreferred, nbPremix); // cap with nbPremixPreferred
    nbPremix = Math.min(NB_PREMIX_MAX, nbPremix); // cap with UTXO NB_PREMIX_MAX

    // at least 1 nbPremix
    if (nbPremix < 1) {
      throw new Exception(
          "Invalid nbPremix detected, please report this bug. nbPremix="
              + nbPremix
              + " for spendFromBalance="
              + spendFromBalance
              + ", feeSatPerByte="
              + feeSatPerByte
              + ", premixValue="
              + premixValue);
    }

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
    for (int j = 0; j < nbPremix; j++) {
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

    // fee selection
    long tx0MinerFee = computeTx0MinerFee(nbPremix, feeSatPerByte, depositSpendFrom);

    // change
    long spendValue = computeTx0SpendValue(premixValue, nbPremix, tx0MinerFee);
    long changeValue = spendFromBalance - spendValue;
    if (changeValue > 0) {
      //
      // 1 change output
      //
      HD_Address changeAddress = depositWallet.getNextChangeAddress();
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
      if (changeValue < 0) {
        throw new Exception(
            "Negative change detected, please report this bug. changeValue="
                + changeValue
                + ", tx0MinerFee="
                + tx0MinerFee);
      }
    }

    // samourai fee
    TransactionOutput txSWFee = bech32Util.getTransactionOutput(feeAddressBech32, feeValue, params);
    outputs.add(txSWFee);
    if (log.isDebugEnabled()) {
      log.debug("Tx0 out (fee): feeAddress=" + feeAddressBech32 + " (" + feeValue + " sats)");
    }

    // add OP_RETURN output
    Script op_returnOutputScript =
        new ScriptBuilder().op(ScriptOpCodes.OP_RETURN).data(opReturnValue).build();
    TransactionOutput txFeeOutput =
        new TransactionOutput(params, null, Coin.valueOf(0L), op_returnOutputScript.getProgram());
    outputs.add(txFeeOutput);
    if (log.isDebugEnabled()) {
      log.debug("Tx0 out (OP_RETURN): " + opReturnValue.length + " bytes");
    }
    if (opReturnValue.length != WhirlpoolFee.FEE_LENGTH) {
      throw new Exception(
          "Invalid opReturnValue length detected, please report this bug. opReturnValue="
              + opReturnValue
              + " vs "
              + WhirlpoolFee.FEE_LENGTH);
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

  private String computeFeeAddressToXpub(String xpubFee, int feeIndice) {
    DeterministicKey mKey = FormatsUtilGeneric.getInstance().createMasterPubKeyFromXPub(xpubFee);
    DeterministicKey cKey =
        HDKeyDerivation.deriveChildKey(
            mKey, new ChildNumber(0, false)); // assume external/receive chain
    DeterministicKey adk = HDKeyDerivation.deriveChildKey(cKey, new ChildNumber(feeIndice, false));
    ECKey feePubkey = ECKey.fromPublicOnly(adk.getPubKey());
    String feeAddressBech32 = bech32Util.toBech32(feePubkey.getPubKey(), params);
    return feeAddressBech32;
  }

  /*
  public WhirlpoolUtxo findUtxoDepositForTx0(
      Pool pool, int feeSatPerByte, Collection<WhirlpoolUtxo> depositUtxos) throws Exception {
    return findUtxoDepositForTx0(pool, feeSatPerByte, depositUtxos, NB_PREMIX_MAX, 1);
  }

  public WhirlpoolUtxo findUtxoDepositForTx0(
      Pool pool,
      int feeSatPerByte,
      Collection<WhirlpoolUtxo> depositUtxos,
      int nbOutputsPreferred,
      int nbOutputsMin)
      throws Exception {

    // find utxo to spend Tx0 from
    final long spendFromBalanceMin = computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsMin);
    final long spendFromBalancePreferred =
        computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsPreferred);

    List<WhirlpoolUtxo> depositSpendFroms =
        filterUtxosByBalancePreferred(spendFromBalanceMin, spendFromBalancePreferred, depositUtxos);
    if (depositSpendFroms.isEmpty()) {
      throw new EmptyWalletException("Insufficient balance for Tx0", spendFromBalanceMin);
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Found "
              + depositSpendFroms.size()
              + " utxos to use as Tx0 input for spendFromBalanceMin="
              + spendFromBalanceMin
              + ", spendFromBalancePreferred="
              + spendFromBalancePreferred
              + ", nbOutputsMin="
              + nbOutputsMin
              + ", nbOutputsPreferred="
              + nbOutputsPreferred);
      ClientUtils.logWhirlpoolUtxos(depositSpendFroms);
    }
    WhirlpoolUtxo whirlpoolUtxoSpendFrom = depositSpendFroms.get(0);
    return whirlpoolUtxoSpendFrom;
  }

  private List<WhirlpoolUtxo> filterUtxosByBalancePreferred(
      final long balanceMin, final long balancePreferred, Collection<WhirlpoolUtxo> utxos) {
    if (utxos.isEmpty()) {
      return new ArrayList<WhirlpoolUtxo>();
    }
    return StreamSupport.stream(utxos)
        .filter(
            new Predicate<WhirlpoolUtxo>() {
              @Override
              public boolean test(WhirlpoolUtxo utxo) {
                return utxo.getUtxo().value >= balanceMin;
              }
            })

        // take UTXO closest to balancePreferred (and higher when possible)
        .sorted(new UnspentOutputPreferredAmountMinComparator(balancePreferred))
        .collect(Collectors.<WhirlpoolUtxo>toList());
  }*/

  public Collection<Pool> findPools(
      WhirlpoolUtxo depositUtxo,
      Collection<Pool> poolsByPriority,
      int feeSatPerByte,
      int nbOutputsPreferred,
      int nbOutputsMin) {
    List<Pool> eligiblePools = new LinkedList<Pool>();

    // first, add pools eligible for: nbOutputsPreferred
    for (Pool pool : poolsByPriority) {
      long balanceMin = computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsPreferred);
      if (depositUtxo.getUtxo().value >= balanceMin) {
        eligiblePools.add(pool);
      }
    }

    // then add pools eligible for: nbOutputsMin
    for (Pool pool : poolsByPriority) {
      if (!eligiblePools.contains(pool)) {
        long balanceMin = computeSpendFromBalanceMin(pool, feeSatPerByte, nbOutputsMin);
        if (depositUtxo.getUtxo().value >= balanceMin) {
          eligiblePools.add(pool);
        }
      }
    }
    return eligiblePools;
  }
}
