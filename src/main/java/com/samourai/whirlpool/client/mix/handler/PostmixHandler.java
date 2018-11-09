package com.samourai.whirlpool.client.mix.handler;

import com.samourai.wallet.bip47.BIP47UtilGeneric;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import java.lang.invoke.MethodHandles;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostmixHandler implements IPostmixHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private Bech32UtilGeneric bech32Util = Bech32UtilGeneric.getInstance();

  private static final int BIP47_ACCOUNT_RECEIVE = Integer.MAX_VALUE;
  private static final int BIP47_ACCOUNT_COUNTERPARTY = Integer.MAX_VALUE - 1;

  private BIP47Wallet bip47Wallet;
  private ECKey receiveKey;
  private int paymentCodeIndex;
  private BIP47UtilGeneric bip47Util;

  public PostmixHandler(BIP47Wallet bip47Wallet, int paymentCodeIndex, BIP47UtilGeneric bip47Util) {
    this.bip47Wallet = bip47Wallet;
    this.receiveKey = null;
    this.paymentCodeIndex = paymentCodeIndex;
    this.bip47Util = bip47Util;
  }

  @Override
  public String computeReceiveAddress(NetworkParameters params) throws Exception {
    // compute receiveAddress with our own paymentCode counterparty
    PaymentCode paymentCodeCounter = computePaymentCodeCounterparty();
    PaymentAddress receiveAddress =
        bip47Util.getReceiveAddress(
            bip47Wallet, BIP47_ACCOUNT_RECEIVE, paymentCodeCounter, this.paymentCodeIndex, params);

    // bech32
    this.receiveKey = receiveAddress.getReceiveECKey();
    String bech32Address = bech32Util.toBech32(receiveKey.getPubKey(), params);
    if (log.isDebugEnabled()) {
      log.debug(
          "receiveAddress="
              + bech32Address
              + " (bip47AccountReceive="
              + BIP47_ACCOUNT_RECEIVE
              + ", paymentCodeIndex="
              + paymentCodeIndex
              + ")");
      log.debug("receiveECKey=" + this.receiveKey.getPrivateKeyAsWiF(params));
    }
    this.paymentCodeIndex++;
    return bech32Address;
  }

  private PaymentCode computePaymentCodeCounterparty() throws Exception {
    PaymentCode paymentCodeCounter =
        new PaymentCode(this.bip47Wallet.getAccount(BIP47_ACCOUNT_COUNTERPARTY).getPaymentCode());
    if (!paymentCodeCounter.isValid()) {
      throw new Exception("Invalid paymentCode");
    }
    return paymentCodeCounter;
  }

  @Override
  public IPremixHandler computeNextPremixHandler(UtxoWithBalance receiveUtxo) {
    PremixHandler nextPremixHandler = new PremixHandler(receiveUtxo, receiveKey);
    return nextPremixHandler;
  }

  public ECKey getReceiveKey() {
    return receiveKey;
  }
}
