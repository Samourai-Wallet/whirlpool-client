package com.samourai.whirlpool.client.mix.handler;

import com.samourai.whirlpool.client.utils.ClientUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public class PremixHandler implements IPremixHandler {
    private UtxoWithBalance utxo;
    private ECKey utxoKey;

    public PremixHandler(UtxoWithBalance utxo, ECKey utxoKey) {
        this.utxo = utxo;
        this.utxoKey = utxoKey;
    }

    @Override
    public UtxoWithBalance getUtxo() {
        return utxo;
    }

    @Override
    public void signTransaction(Transaction tx, int inputIndex, NetworkParameters params) throws Exception {
        long spendAmount = utxo.getBalance();
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
}
