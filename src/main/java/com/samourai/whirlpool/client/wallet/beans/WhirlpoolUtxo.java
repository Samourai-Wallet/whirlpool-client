package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.NotifiableException;

public class WhirlpoolUtxo {
  private static final int MIX_TARGET_DEFAULT = 1;
  private static final int PRIORITY_DEFAULT = 5;

  private UnspentOutput utxo;
  private WhirlpoolAccount account;
  private WhirlpoolUtxoStatus status;
  private Integer progressPercent;
  private String progressLabel;
  private String message;
  private String error;
  private Long lastActivity;
  private WhirlpoolUtxoConfig utxoConfig;

  public WhirlpoolUtxo(
      UnspentOutput utxo, WhirlpoolAccount account, WhirlpoolUtxoStatus status, int mixsTarget) {
    this.utxo = utxo;
    this.account = account;
    this.status = status;
    this.progressPercent = null;
    this.progressLabel = null;
    this.message = null;
    this.error = null;
    this.lastActivity = null;
    this.utxoConfig = new WhirlpoolUtxoConfig(mixsTarget);
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

  public WhirlpoolUtxoConfig getUtxoConfig() {
    return utxoConfig;
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
        + ", ["
        + utxoConfig
        + "]"
        + (hasMessage() ? ", message=" + message : "")
        + (hasError() ? ", error=" + error : "")
        + ", utxo="
        + utxo.toString();
  }
}
