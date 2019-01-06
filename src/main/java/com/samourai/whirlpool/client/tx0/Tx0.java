package com.samourai.whirlpool.client.tx0;

import com.samourai.whirlpool.protocol.beans.Utxo;
import java.util.List;
import org.bitcoinj.core.Transaction;

public class Tx0 {
  private Transaction tx;
  private List<Utxo> premixUtxos;

  public Tx0(Transaction tx, List<Utxo> premixUtxos) {
    this.tx = tx;
    this.premixUtxos = premixUtxos;
  }

  public Transaction getTx() {
    return tx;
  }

  public List<Utxo> getPremixUtxos() {
    return premixUtxos;
  }
}
