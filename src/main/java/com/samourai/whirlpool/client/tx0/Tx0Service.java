package com.samourai.whirlpool.client.tx0;

import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.wallet.api.backend.beans.UnspentResponse;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.client.Bip84Wallet;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import com.samourai.wallet.util.FeeUtil;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.utils.BIP69InputComparatorUnspentOutput;
import com.samourai.whirlpool.client.utils.ClientUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.samourai.whirlpool.client.whirlpool.beans.Tx0Data;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.protocol.fee.WhirlpoolFee;
import com.samourai.whirlpool.protocol.rest.Tx0DataResponse;
import java.util.*;
import java8.util.function.ToLongFunction;
import java8.util.stream.StreamSupport;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tx0Service {
  private Logger log = LoggerFactory.getLogger(Tx0Service.class);
  protected static final int NB_PREMIX_MAX = 600;

  private final Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();
  private final WhirlpoolFee whirlpoolFee;
  private final FeeUtil feeUtil = FeeUtil.getInstance();

  private WhirlpoolWalletConfig config;

  public Tx0Service(WhirlpoolWalletConfig config) {
    this.config = config;
    whirlpoolFee = WhirlpoolFee.getInstance(config.getSecretPointFactory());
  }

  private long computePremixValue(Pool pool, int feePremix) {
    // compute minerFeePerMustmix
    long txPremixFeesEstimate =
        feeUtil.estimatedFeeSegwit(
            0, 0, pool.getMixAnonymitySet(), pool.getMixAnonymitySet(), 0, feePremix);
    long minerFeePerMustmix = txPremixFeesEstimate / pool.getMinMustMix();
    // pool.getMixAnonymitySet();
    long premixValue = pool.getDenomination() + minerFeePerMustmix;

    // make sure destinationValue is acceptable for pool
    long premixBalanceMin = pool.computePremixBalanceMin(false);
    long premixBalanceCap = pool.computePremixBalanceCap(false);
    long premixBalanceMax = pool.computePremixBalanceMax(false);

    long premixValueFinal = premixValue;
    premixValueFinal = Math.min(premixValueFinal, premixBalanceMax);
    premixValueFinal = Math.min(premixValueFinal, premixBalanceCap);
    premixValueFinal = Math.max(premixValueFinal, premixBalanceMin);

    if (log.isDebugEnabled()) {
      log.debug(
          "premixValueFinal="
              + premixValueFinal
              + ", premixValue="
              + premixValue
              + ", minerFeePerMustmix="
              + minerFeePerMustmix
              + ", txPremixFeesEstimate="
              + txPremixFeesEstimate
              + " for poolId="
              + pool.getPoolId());
    }
    return premixValueFinal;
  }

  private int computeNbPremixMax(
      long premixValue,
      Collection<? extends UnspentResponse.UnspentOutput> spendFrom,
      long samouraiFee,
      int feeTx0) {
    long spendFromBalance = computeSpendFromBalance(spendFrom);

    // compute nbPremix ignoring TX0 fee
    int nbPremixInitial = (int) Math.ceil(spendFromBalance / premixValue);

    // compute nbPremix with TX0 fee
    int nbPremix = nbPremixInitial;
    while (true) {
      // estimate TX0 fee for nbPremix
      long tx0MinerFee = computeTx0MinerFee(nbPremix, feeTx0, spendFrom);
      long spendValue = computeTx0SpendValue(premixValue, nbPremix, samouraiFee, tx0MinerFee);
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

  protected long computeTx0MinerFee(
      int nbPremix, long feeTx0, Collection<? extends UnspentResponse.UnspentOutput> spendFroms) {
    int nbOutputsNonOpReturn = nbPremix + 2; // outputs + change + fee

    // spendFroms can be NULL (for fee simulation)
    int nbSpendFroms = (spendFroms != null ? spendFroms.size() : 1);

    // spend from N bech32 input
    long tx0MinerFee =
        feeUtil.estimatedFeeSegwit(0, 0, nbSpendFroms, nbOutputsNonOpReturn, 1, feeTx0);

    if (log.isDebugEnabled()) {
      log.debug(
          "tx0 minerFee: "
              + tx0MinerFee
              + "sats, totalBytes="
              + "b for nbPremix="
              + nbPremix
              + ", feeTx0="
              + feeTx0);
    }
    return tx0MinerFee;
  }

  private long computeTx0SpendValue(
      long premixValue, int nbPremix, long samouraiFee, long tx0MinerFee) {
    long changeValue = (premixValue * nbPremix) + samouraiFee + tx0MinerFee;
    return changeValue;
  }

  public long computeSpendFromBalanceMin(Pool pool, int feeTx0, int feePremix, int nbPremix) {
    long premixValue = computePremixValue(pool, feePremix);
    long tx0MinerFee = computeTx0MinerFee(nbPremix, feeTx0, null);
    long samouraiFee = pool.getFeeValue();
    long spendValue = computeTx0SpendValue(premixValue, nbPremix, samouraiFee, tx0MinerFee);
    return spendValue;
  }

  public Tx0Preview tx0Preview(
      Collection<UnspentOutputWithKey> spendFroms,
      Tx0Config tx0Config,
      int feeTx0,
      int feePremix,
      Pool pool)
      throws Exception {

    // check balance min
    final long spendFromBalanceMin =
        config.getTx0Service().computeSpendFromBalanceMin(pool, feeTx0, feePremix, 1);

    long spendFromBalance = computeSpendFromBalance(spendFroms);
    if (spendFromBalance < spendFromBalanceMin) {
      throw new NotifiableException(
          "Insufficient utxo value for Tx0: " + spendFromBalance + " < " + spendFromBalanceMin);
    }

    // fetch fresh Tx0Data
    Tx0Data tx0Data = fetchTx0Data(pool.getPoolId());

    long premixValue = computePremixValue(pool, feePremix);
    long samouraiFee = (tx0Data.getFeeValue() > 0 ? tx0Data.getFeeValue() : 0);
    int nbPremix = computeNbPremix(spendFroms, tx0Config, feeTx0, premixValue, samouraiFee);
    long minerFee = computeTx0MinerFee(nbPremix, feeTx0, spendFroms);
    long spendValue = computeTx0SpendValue(premixValue, nbPremix, samouraiFee, minerFee);
    long changeValue = spendFromBalance - spendValue;

    return new Tx0Preview(tx0Data, minerFee, samouraiFee, premixValue, changeValue, nbPremix);
  }

  /** Generate maxOutputs premixes outputs max. */
  public Tx0 tx0(
      Collection<UnspentOutputWithKey> spendFroms,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      Bip84Wallet badbankWallet,
      Tx0Config tx0Config,
      int feeTx0,
      int feePremix,
      Pool pool)
      throws Exception {

    // compute & preview
    Tx0Preview tx0Preview = tx0Preview(spendFroms, tx0Config, feeTx0, feePremix, pool);

    log.info(
        " • Tx0: spendFrom="
            + spendFroms
            + ", poolId="
            + pool.getPoolId()
            + ", maxOutputs="
            + (tx0Config.getMaxOutputs() != null ? tx0Config.getMaxOutputs() : "*")
            + ", badbankChange="
            + tx0Config.isBadbankChange()
            + ", tx0Preview=["
            + tx0Preview
            + "]");

    return tx0(spendFroms, depositWallet, premixWallet, badbankWallet, tx0Config, tx0Preview);
  }

  protected Tx0 tx0(
      Collection<UnspentOutputWithKey> spendFroms,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      Bip84Wallet badbankWallet,
      Tx0Config tx0Config,
      Tx0Preview tx0Preview)
      throws Exception {
    NetworkParameters params = config.getNetworkParameters();

    Tx0Data tx0Data = tx0Preview.getTx0Data();

    // compute opReturnValue for feePaymentCode and feePayload
    byte[] feePayload = tx0Data.getFeePayload();
    int feeIndice;
    String feeAddressBech32;
    long samouraiFee;
    if (tx0Data.getFeeValue() > 0) {
      // pay to fee
      feeIndice = tx0Data.getFeeIndice();
      feeAddressBech32 = tx0Data.getFeeAddress();
      samouraiFee = tx0Data.getFeeValue();
      if (log.isDebugEnabled()) {
        log.debug(
            "feeAddressDestination: samourai => feeAddress="
                + feeAddressBech32
                + ", feeIndice="
                + feeIndice
                + ", samouraiFee="
                + samouraiFee);
      }
    } else {
      // pay to deposit
      feeIndice = 0;
      feeAddressBech32 = bech32Util.toBech32(depositWallet.getNextChangeAddress(), params);
      samouraiFee = tx0Data.getFeeChange();
      if (log.isDebugEnabled()) {
        log.debug(
            "feeAddressDestination: deposit => feeAddress="
                + feeAddressBech32
                + ", samouraiFee="
                + samouraiFee);
      }
    }

    // sort inputs now, we need to know the first input for OP_RETURN encode
    List<UnspentOutputWithKey> sortedSpendFroms = new LinkedList<UnspentOutputWithKey>();
    sortedSpendFroms.addAll(spendFroms);
    Collections.sort(sortedSpendFroms, new BIP69InputComparatorUnspentOutput());

    UnspentOutputWithKey firstInput = sortedSpendFroms.get(0);
    String feePaymentCode = tx0Data.getFeePaymentCode();
    byte[] opReturnValue =
        whirlpoolFee.encode(
            feeIndice,
            feePayload,
            feePaymentCode,
            params,
            firstInput.getKey(),
            firstInput.computeOutpoint(params));
    if (log.isDebugEnabled()) {
      log.debug(
          "computing opReturnValue for feeIndice="
              + feeIndice
              + ", feePayloadHex="
              + (feePayload != null ? Hex.toHexString(feePayload) : "null"));
    }
    return tx0(
        sortedSpendFroms,
        depositWallet,
        premixWallet,
        badbankWallet,
        tx0Config,
        tx0Preview,
        opReturnValue,
        feeAddressBech32);
  }

  protected Tx0 tx0(
      List<UnspentOutputWithKey> sortedSpendFroms,
      Bip84Wallet depositWallet,
      Bip84Wallet premixWallet,
      Bip84Wallet badbankWallet,
      Tx0Config tx0Config,
      Tx0Preview tx0Preview,
      byte[] opReturnValue,
      String feeAddressBech32)
      throws Exception {

    Bip84Wallet changeWallet = tx0Config.isBadbankChange() ? badbankWallet : depositWallet;

    //
    // tx0
    //

    Tx0 tx0 =
        buildTx0(
            sortedSpendFroms,
            premixWallet,
            tx0Preview,
            opReturnValue,
            feeAddressBech32,
            changeWallet,
            config.getNetworkParameters());

    Transaction tx = tx0.getTx();
    final String hexTx = new String(Hex.encode(tx.bitcoinSerialize()));
    final String strTxHash = tx.getHashAsString();

    tx.verify();
    // System.out.println(tx);
    if (log.isDebugEnabled()) {
      log.debug("Tx0 hash: " + strTxHash);
      log.debug("Tx0 hex: " + hexTx);
      long feePrice = tx0Preview.getMinerFee() / tx.getVirtualTransactionSize();
      log.debug("Tx0 size: " + tx.getVirtualTransactionSize() + "b, feePrice=" + feePrice + "s/b");
    }
    return tx0;
  }

  private int computeNbPremix(
      Collection<UnspentOutputWithKey> sortedSpendFroms,
      Tx0Config tx0Config,
      int feeTx0,
      long premixValue,
      long samouraiFee) {
    int nbPremix =
        computeNbPremixMax(
            premixValue,
            sortedSpendFroms,
            samouraiFee,
            feeTx0); // cap with balance and tx0 minerFee
    if (tx0Config.getMaxOutputs() != null) {
      nbPremix = Math.min(tx0Config.getMaxOutputs(), nbPremix); // cap with maxOutputs
    }
    nbPremix = Math.min(NB_PREMIX_MAX, nbPremix); // cap with UTXO NB_PREMIX_MAX
    return nbPremix;
  }

  protected long computeSpendFromBalance(
      Collection<? extends UnspentResponse.UnspentOutput> spendFroms) {
    long balance =
        StreamSupport.stream(spendFroms)
            .mapToLong(
                new ToLongFunction<UnspentResponse.UnspentOutput>() {
                  @Override
                  public long applyAsLong(UnspentResponse.UnspentOutput unspentOutput) {
                    return unspentOutput.value;
                  }
                })
            .sum();
    return balance;
  }

  protected Tx0 buildTx0(
      Collection<UnspentOutputWithKey> sortedSpendFroms,
      Bip84Wallet premixWallet,
      Tx0Preview tx0Preview,
      byte[] opReturnValue,
      String feeAddressBech32,
      Bip84Wallet changeWallet,
      NetworkParameters params)
      throws Exception {

    long premixValue = tx0Preview.getPremixValue();
    long samouraiFee = tx0Preview.getPoolFee();
    int nbPremix = tx0Preview.getNbPremix();
    long tx0MinerFee = tx0Preview.getMinerFee();
    long changeValue = tx0Preview.getChangeValue();

    // verify

    if (sortedSpendFroms.size() <= 0) {
      throw new IllegalArgumentException("spendFroms should be > 0");
    }

    if (samouraiFee <= 0) {
      throw new IllegalArgumentException("samouraiFee should be > 0");
    }

    // at least 1 premix
    if (nbPremix < 1) {
      throw new Exception("Invalid nbPremix=" + nbPremix);
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
    List<TransactionOutput> premixOutputs = new ArrayList<TransactionOutput>();
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
      premixOutputs.add(txOutSpend);
    }

    TransactionOutput changeOutput = null;
    if (changeValue > 0) {
      //
      // 1 change output
      //
      HD_Address changeAddress = changeWallet.getNextChangeAddress();
      String changeAddressBech32 = bech32Util.toBech32(changeAddress, params);
      changeOutput = bech32Util.getTransactionOutput(changeAddressBech32, changeValue, params);
      outputs.add(changeOutput);
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
    TransactionOutput txSWFee =
        bech32Util.getTransactionOutput(feeAddressBech32, samouraiFee, params);
    outputs.add(txSWFee);
    if (log.isDebugEnabled()) {
      log.debug("Tx0 out (fee): feeAddress=" + feeAddressBech32 + " (" + samouraiFee + " sats)");
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

    // all inputs
    for (UnspentOutputWithKey spendFrom : sortedSpendFroms) {
      buildTx0Input(tx, spendFrom, params);
      if (log.isDebugEnabled()) {
        log.debug("Tx0 in: utxo=" + spendFrom);
      }
    }

    signTx0(tx, sortedSpendFroms, params);
    tx.verify();

    Tx0 tx0 = new Tx0(tx0Preview, tx, premixOutputs, changeOutput);
    return tx0;
  }

  protected void buildTx0Input(
      Transaction tx, UnspentOutputWithKey input, NetworkParameters params) {
    ECKey spendFromKey = ECKey.fromPrivate(input.getKey());
    TransactionOutPoint depositSpendFrom = input.computeOutpoint(params);
    final Script segwitPubkeyScript = ScriptBuilder.createP2WPKHOutputScript(spendFromKey);
    tx.addSignedInput(depositSpendFrom, segwitPubkeyScript, spendFromKey);
  }

  protected void signTx0(
      Transaction tx, Collection<UnspentOutputWithKey> inputs, NetworkParameters params) {
    // inputs were already signed
  }

  public Collection<Pool> findPools(
      int nbOutputsMin,
      Collection<Pool> poolsByPreference,
      long utxoValue,
      int feeTx0,
      int feePremix) {
    List<Pool> eligiblePools = new LinkedList<Pool>();
    for (Pool pool : poolsByPreference) {
      boolean eligible = isTx0Possible(utxoValue, pool, feeTx0, feePremix, nbOutputsMin);
      if (eligible) {
        eligiblePools.add(pool);
      }
    }
    return eligiblePools;
  }

  public boolean isTx0Possible(
      long utxoValue, Pool pool, int feeTx0, int feePremix, int nbOutputsMin) {
    long balanceMin = computeSpendFromBalanceMin(pool, feeTx0, feePremix, nbOutputsMin);
    if (log.isDebugEnabled()) {
      log.debug(
          "isTx0Possible["
              + pool.getPoolId()
              + "] spendFromBalanceMin="
              + balanceMin
              + " for nbOutputsMin="
              + nbOutputsMin
              + ", utxoValue="
              + utxoValue
              + ", feeTx0="
              + feeTx0
              + ", feePremix="
              + feePremix);
    }
    return (utxoValue >= balanceMin);
  }

  protected Tx0Data fetchTx0Data(String poolId) throws HttpException, NotifiableException {
    String url = WhirlpoolProtocol.getUrlTx0Data(config.getServer(), poolId, config.getScode());
    try {
      Tx0DataResponse tx0Response =
          config.getHttpClient().getJson(url, Tx0DataResponse.class, null);
      byte[] feePayload = WhirlpoolProtocol.decodeBytes(tx0Response.feePayload64);
      Tx0Data tx0Data =
          new Tx0Data(
              tx0Response.feePaymentCode,
              tx0Response.feeValue,
              tx0Response.feeChange,
              // tx0Response.message,
              feePayload,
              tx0Response.feeAddress,
              tx0Response.feeIndice);
      return tx0Data;
    } catch (HttpException e) {
      String restErrorResponseMessage = ClientUtils.parseRestErrorMessage(e);
      if (restErrorResponseMessage != null) {
        throw new NotifiableException(restErrorResponseMessage);
      }
      throw e;
    }
  }
}
