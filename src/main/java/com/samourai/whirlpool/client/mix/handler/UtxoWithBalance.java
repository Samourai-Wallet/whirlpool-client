package com.samourai.whirlpool.client.mix.handler;

import com.samourai.whirlpool.protocol.beans.Utxo;

public class UtxoWithBalance extends Utxo {

  private long balance;

  public UtxoWithBalance(String hash, long index, long balance) {
    super(hash, index);
    this.balance = balance;
  }

  public UtxoWithBalance(Utxo utxo, long balance) {
    super(utxo.getHash(), utxo.getIndex());
    this.balance = balance;
  }

  public long getBalance() {
    return balance;
  }

  public String toString() {
    return getHash() + "-" + getIndex() + ", balance=" + balance + "sats";
  }
}
