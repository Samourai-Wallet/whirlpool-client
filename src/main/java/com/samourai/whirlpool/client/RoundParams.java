package com.samourai.whirlpool.client;

import com.samourai.whirlpool.client.simple.ISimpleWhirlpoolClient;

public class RoundParams {
    private String utxoHash;
    private long utxoIdx;
    private String paymentCode;
    private ISimpleWhirlpoolClient simpleWhirlpoolClient;
    private boolean liquidity;

    public RoundParams( String utxoHash, long utxoIdx, String paymentCode, ISimpleWhirlpoolClient simpleWhirlpoolClient, boolean liquidity) {
        this.utxoHash = utxoHash;
        this.utxoIdx = utxoIdx;
        this.paymentCode = paymentCode;
        this.simpleWhirlpoolClient = simpleWhirlpoolClient;
        this.liquidity = liquidity;
    }

    public String getUtxoHash() {
        return utxoHash;
    }

    public long getUtxoIdx() {
        return utxoIdx;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public ISimpleWhirlpoolClient getSimpleWhirlpoolClient() {
        return simpleWhirlpoolClient;
    }

    public boolean isLiquidity() {
        return liquidity;
    }
}
