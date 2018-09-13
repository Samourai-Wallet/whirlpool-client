package com.samourai.whirlpool.client.mix.handler;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public interface IMixHandler {

    String computeReceiveAddress(NetworkParameters params) throws Exception;

    void signTransaction(Transaction tx, int inputIndex, long spendAmount, NetworkParameters params)  throws Exception;

    String signMessage(String message);

    byte[] getPubkey();

    IMixHandler computeMixHandlerForNextMix();

}
