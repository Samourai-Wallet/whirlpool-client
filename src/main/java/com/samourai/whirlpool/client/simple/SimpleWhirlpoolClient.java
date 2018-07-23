package com.samourai.whirlpool.client.simple;

import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.whirlpool.client.utils.ClientUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class SimpleWhirlpoolClient implements ISimpleWhirlpoolClient {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ECKey utxoKey;
    private BIP47Wallet bip47Wallet;
    private ECKey receiveKey;

    public SimpleWhirlpoolClient(ECKey utxoKey, BIP47Wallet bip47Wallet) {
        this.utxoKey = utxoKey;
        this.bip47Wallet = bip47Wallet;
        this.receiveKey = null;
    }

    private int computeNextPaymentCodeSendIndex() {
        return 0; // TODO external client should compute this value
    }

    private int computeNextPaymentCodeReceiveIndex() {
        return 0; // TODO external client should compute this value
    }

    @Override
    public String computeSendAddress(String toPeerPaymentCode, NetworkParameters params) throws Exception {
        // calculates address with receiver payment code
        int idx = computeNextPaymentCodeSendIndex();
        PaymentAddress sendAddress = BIP47Util.getInstance().getSendAddress(bip47Wallet, new PaymentCode(toPeerPaymentCode), idx, params);

        // sender calculates from pubkey
        SegwitAddress addressFromSender = new SegwitAddress(sendAddress.getSendECKey().getPubKey(), params);
        return addressFromSender.getBech32AsString();
    }

    @Override
    public String computeReceiveAddress(String fromPeerPaymentCode, NetworkParameters params) throws Exception {
        // receiver calculates address with sender's payment code
        int idx = computeNextPaymentCodeReceiveIndex();
        PaymentAddress receiveAddress = BIP47Util.getInstance().getReceiveAddress(bip47Wallet, new PaymentCode(fromPeerPaymentCode), idx, params);

        // receiver can calculate from privkey
        this.receiveKey = receiveAddress.getReceiveECKey();
        if (log.isDebugEnabled()) {
            log.debug("receiveECKey=" + this.receiveKey.getPrivateKeyAsWiF(params));
        }
        SegwitAddress addressToReceiver = new SegwitAddress(receiveKey, params);
        return addressToReceiver.getBech32AsString();
    }

    @Override
    public void signTransaction(Transaction tx, int inputIndex, long spendAmount, NetworkParameters params) throws Exception {
        ClientUtils.signSegwitInput(tx, inputIndex, utxoKey, spendAmount, params);
    }

    @Override
    public String signMessage(String message) {
        return utxoKey.signMessage(message);
    }

    @Override
    public byte[] getPubkey() {
        return utxoKey.getPubKey();
    }

    @Override
    public void postHttpRequest(String url, Object requestBody) throws Exception {
        // TODO use TOR
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity result = restTemplate.postForEntity(url, requestBody, null);
        if (result == null || !result.getStatusCode().is2xxSuccessful()) {
            // response error
            throw new Exception("unable to registerOutput");
        }
    }

    @Override
    public ISimpleWhirlpoolClient computeSimpleWhirlpoolClientForNextRound() {
        return new SimpleWhirlpoolClient(receiveKey, bip47Wallet);
    }
}
