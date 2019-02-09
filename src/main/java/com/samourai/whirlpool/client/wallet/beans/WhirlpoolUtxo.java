package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.wallet.WhirlpoolAccount;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;

public class WhirlpoolUtxo {
  private UnspentOutput utxo;
  private WhirlpoolAccount account;
  private WhirlpoolUtxoStatus status;
  private Pool pool;
  private int mixsTarget;
  private int mixsDone;
  private String message;
  private String error;

  public WhirlpoolUtxo(UnspentOutput utxo, WhirlpoolAccount account, WhirlpoolUtxoStatus status) {
    this.utxo = utxo;
    this.account = account;
    this.status = status;
    this.pool = null;
    this.mixsTarget = 1;
    this.mixsDone = 0;
    this.message = null;
    this.error = null;
  }

  public UnspentOutput getUtxo() {
    return utxo;
  }

  public WhirlpoolAccount getAccount() {
    return account;
  }

  public WhirlpoolUtxoStatus getStatus() {
    return status;
  }

  public void setPool(Pool pool) {
    this.pool = pool;
  }

  public Pool getPool() {
    return pool;
  }

  public void setMixsTarget(int mixsTarget) {
    this.mixsTarget = mixsTarget;
  }

  public int getMixsTarget() {
    return mixsTarget;
  }

  public void incrementMixsDone(int mixsDone) {
    this.mixsDone = mixsDone;
  }

  public int getMixsDone() {
    return mixsDone;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public boolean hasMessage() {
    return message != null;
  }

  public String getMessage() {
    return message;
  }

  public void setError(Exception e) {
    String message = NotifiableException.computeNotifiableException(e).getMessage();
    setError(message);
  }

  public void setError(String error) {
    this.error = error;
  }

  public boolean hasError() {
    return error != null;
  }

  public void setStatus(WhirlpoolUtxoStatus status) {
    this.status = status;
    this.error = null;
  }

  public void setUtxo(UnspentOutput utxo) {
    this.utxo = utxo;
  }

  @Override
  public String toString() {
    return "status="
        + status
        + ", account="
        + account
        + (pool != null ? ", poolId=" + pool.getPoolId() : "")
        + (hasMessage() ? ", message=" + message : "")
        + (hasError() ? ", error=" + error : "")
        + ", utxo="
        + utxo.toString();
  }
}
