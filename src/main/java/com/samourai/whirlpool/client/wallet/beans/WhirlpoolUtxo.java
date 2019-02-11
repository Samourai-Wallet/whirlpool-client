package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.wallet.WhirlpoolAccount;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;

public class WhirlpoolUtxo {
  private static final int MIX_TARGET_DEFAULT = 1;
  private static final int PRIORITY_DEFAULT = 5;

  private UnspentOutput utxo;
  private WhirlpoolAccount account;
  private WhirlpoolUtxoStatus status;
  private Integer progressPercent;
  private String progressLabel;
  private Pool pool;
  private int priority;
  private int mixsTarget;
  private int mixsDone;
  private String message;
  private String error;
  private Long lastActivity;

  public WhirlpoolUtxo(UnspentOutput utxo, WhirlpoolAccount account, WhirlpoolUtxoStatus status) {
    this.utxo = utxo;
    this.account = account;
    this.status = status;
    this.progressPercent = null;
    this.progressLabel = null;
    this.pool = null;
    this.priority = PRIORITY_DEFAULT;
    this.mixsTarget = MIX_TARGET_DEFAULT;
    this.mixsDone = 0;
    this.message = null;
    this.error = null;
    this.lastActivity = null;
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

  public Integer getProgressPercent() {
    return progressPercent;
  }

  public String getProgressLabel() {
    return progressLabel;
  }

  public void setProgress(Integer progressPercent, String progressLabel) {
    this.progressPercent = progressPercent;
    this.progressLabel = progressLabel;
    setLastActivity();
  }

  public void setPool(Pool pool) {
    this.pool = pool;
  }

  public Pool getPool() {
    return pool;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
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
    setLastActivity();
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
    setLastActivity();
  }

  public boolean hasError() {
    return error != null;
  }

  public String getError() {
    return error;
  }

  public void setStatus(WhirlpoolUtxoStatus status, Integer progressPercent, String progressLabel) {
    this.status = status;
    this.progressPercent = progressPercent;
    this.progressLabel = progressLabel;
    this.error = null;
    setLastActivity();
  }

  public void setStatus(WhirlpoolUtxoStatus status) {
    setStatus(status, null, null);
  }

  public void setStatus(WhirlpoolUtxoStatus status, int progressPercent) {
    setStatus(status, progressPercent, null);
  }

  public void setUtxo(UnspentOutput utxo) {
    this.utxo = utxo;
  }

  public Long getLastActivity() {
    return lastActivity;
  }

  public void setLastActivity() {
    this.lastActivity = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    String progressStr = "";
    if (progressPercent != null) {
      progressStr += progressPercent + "%";
    }
    if (progressLabel != null) {
      progressStr += " " + progressLabel;
    }

    return "account="
        + account
        + ", status="
        + status
        + (!progressStr.isEmpty() ? " (" + progressStr + ")" : "")
        + (pool != null ? ", poolId=" + pool.getPoolId() : "")
        + (hasMessage() ? ", message=" + message : "")
        + (hasError() ? ", error=" + error : "")
        + ", utxo="
        + utxo.toString();
  }
}
