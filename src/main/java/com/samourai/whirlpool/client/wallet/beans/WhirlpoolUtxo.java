package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.api.client.beans.UnspentResponse.UnspentOutput;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;

public class WhirlpoolUtxo {
  private UnspentOutput utxo;
  private WhirlpoolAccount account;
  private WhirlpoolUtxoStatus status;
  private MixStep mixStep;
  private MixableStatus mixableStatus;
  private WhirlpoolWallet wallet;

  private Integer progressPercent;
  private String message;
  private String error;
  private Long lastActivity;

  public WhirlpoolUtxo(
      UnspentOutput utxo,
      WhirlpoolAccount account,
      WhirlpoolUtxoStatus status,
      WhirlpoolWallet wallet) {
    this.utxo = utxo;
    this.account = account;
    this.status = status;
    this.mixStep = null;
    this.mixableStatus = null;
    this.wallet = wallet;

    this.progressPercent = null;
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

  public MixStep getMixStep() {
    return mixStep;
  }

  public Integer getProgressPercent() {
    return progressPercent;
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

  public void setStatus(WhirlpoolUtxoStatus status, MixStep mixStep, Integer progressPercent) {
    this.status = status;
    this.mixStep = mixStep;
    this.progressPercent = progressPercent;
    this.error = null;
    if (!WhirlpoolUtxoStatus.MIX_QUEUE.equals(status)) {
      setLastActivity();
    }
  }

  public void setStatus(WhirlpoolUtxoStatus status) {
    setStatus(status, null, null);
  }

  public void setStatus(WhirlpoolUtxoStatus status, int progressPercent) {
    setStatus(status, null, progressPercent);
  }

  public MixableStatus getMixableStatus() {
    return mixableStatus;
  }

  public void setMixableStatus(MixableStatus mixableStatus) {
    this.mixableStatus = mixableStatus;
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
    return wallet.getUtxoConfig(this);
  }

  @Override
  public String toString() {
    String progressStr = "";
    if (progressPercent != null) {
      progressStr += progressPercent + "%";
    }

    return "account="
        + account
        + ", status="
        + status
        + (!progressStr.isEmpty() ? " (" + progressStr + ")" : "")
        + ", mixStep="
        + (mixStep != null ? mixStep : "null")
        + ", mixableStatus="
        + (mixableStatus != null ? mixableStatus : "null")
        + (hasMessage() ? ", message=" + message : "")
        + (hasError() ? ", error=" + error : "")
        + ", utxo="
        + utxo.toString();
  }
}
