package com.samourai.whirlpool.client.simple;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public interface ISimpleWhirlpoolClient {

    String computeSendAddress(String toPeerPaymentCode, NetworkParameters params) throws Exception;

    String computeReceiveAddress(String fromPeerPaymentCode, NetworkParameters params) throws Exception;

    void signTransaction(Transaction tx, int inputIndex, long spendAmount, NetworkParameters params)  throws Exception;

    String signMessage(String message);

    byte[] getPubkey();

    void postHttpRequest(String url, Object requestBody) throws Exception;

}
