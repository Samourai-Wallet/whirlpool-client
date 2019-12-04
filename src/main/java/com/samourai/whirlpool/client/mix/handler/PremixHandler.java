package com.samourai.whirlpool.client.mix.handler;

import com.samourai.wallet.util.TxUtil;
import com.samourai.whirlpool.client.utils.ClientUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

public class PremixHandler implements IPremixHandler {
  private UtxoWithBalance utxo;
  private ECKey utxoKey;
  private String userPreHash;

  public PremixHandler(UtxoWithBalance utxo, ECKey utxoKey, String userPreHash) {
    this.utxo = utxo;
    this.utxoKey = utxoKey;
    this.userPreHash = userPreHash;
  }

  @Override
  public UtxoWithBalance getUtxo() {
    return utxo;
  }

  @Override
  public void signTransaction(Transaction tx, int inputIndex, NetworkParameters params)
      throws Exception {
    long spendAmount = utxo.getBalance();
    TxUtil.getInstance().signInputSegwit(tx, inputIndex, utxoKey, spendAmount, params);
  }

  @Override
  public String signMessage(String message) {
    return utxoKey.signMessage(message);
  }

  @Override
  public String computeUserHash(String salt) {
    return ClientUtils.sha256Hash(salt + userPreHash);
  }
}
