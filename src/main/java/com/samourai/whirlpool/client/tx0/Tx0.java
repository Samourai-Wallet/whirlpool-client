package com.samourai.whirlpool.client.tx0;

import java.util.List;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

public class Tx0 {
  private Transaction tx;
  private List<TransactionOutput> premixOutputs;
  private TransactionOutput changeOutput;

  public Tx0(
      Transaction tx, List<TransactionOutput> premixOutputs, TransactionOutput changeOutput) {
    this.tx = tx;
    this.premixOutputs = premixOutputs;
    this.changeOutput = changeOutput;
  }

  public Transaction getTx() {
    return tx;
  }

  public List<TransactionOutput> getPremixOutputs() {
    return premixOutputs;
  }

  public TransactionOutput getChangeOutput() {
    return changeOutput;
  }
}
