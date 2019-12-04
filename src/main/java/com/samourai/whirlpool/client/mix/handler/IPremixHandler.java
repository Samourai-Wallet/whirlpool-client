package com.samourai.whirlpool.client.mix.handler;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public interface IPremixHandler {

  UtxoWithBalance getUtxo();

  void signTransaction(Transaction tx, int inputIndex, NetworkParameters params) throws Exception;

  String signMessage(String message);

  String computeUserHash(String salt);
}
