package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.exception.NotifiableException;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class WhirlpoolUtxoState {
  private WhirlpoolUtxoStatus status;
  private MixProgress mixProgress;
  private MixableStatus mixableStatus;

  private String message;
  private String error;

  private Long lastActivity;
  private Long lastError;
  private PublishSubject<WhirlpoolUtxoState> observable;

  public WhirlpoolUtxoState(WhirlpoolUtxoStatus status) {
    this.status = status;
    this.mixProgress = null;
    this.mixableStatus = null;

    this.message = null;
    this.error = null;

    this.lastActivity = null;
    this.lastError = null;
    this.observable = PublishSubject.create();
  }

  private void emit() {
    // notify observers
    observable.onNext(this);
  }

  public WhirlpoolUtxoStatus getStatus() {
    return status;
  }

  public void setStatus(
      WhirlpoolUtxoStatus status, boolean updateLastActivity, MixProgress mixProgress) {
    this.status = status;
    this.mixProgress = mixProgress;
    if (!WhirlpoolUtxoStatus.MIX_QUEUE.equals(status)) {
      this.error = null;
    }
    if (updateLastActivity) {
      setLastActivity();
    }
  }

  public void setStatus(WhirlpoolUtxoStatus status, boolean updateLastActivity) {
    setStatus(status, updateLastActivity, null);
  }

  public MixProgress getMixProgress() {
    return mixProgress;
  }

  public MixableStatus getMixableStatus() {
    return mixableStatus;
  }

  public void setMixableStatus(MixableStatus mixableStatus) {
    this.mixableStatus = mixableStatus;
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
    setLastError();
  }

  public boolean hasError() {
    return error != null;
  }

  public String getError() {
    return error;
  }

  public Long getLastActivity() {
    return lastActivity;
  }

  public void setLastActivity() {
    this.lastActivity = System.currentTimeMillis();
  }

  public void setLastError() {
    this.lastError = System.currentTimeMillis();
  }

  public Long getLastError() {
    return lastError;
  }

  public void setLastError(Long lastError) {
    this.lastError = lastError;
  }

  public Observable<WhirlpoolUtxoState> getObservable() {
    return observable;
  }

  @Override
  public String toString() {
    return "status="
        + status
        + (mixProgress != null ? mixProgress : "")
        + ", mixableStatus="
        + (mixableStatus != null ? mixableStatus : "null")
        + (hasMessage() ? ", message=" + message : "")
        + (hasError() ? ", error=" + error : "")
        + (lastError != null ? ", lastError=" + lastError : "");
  }
}
