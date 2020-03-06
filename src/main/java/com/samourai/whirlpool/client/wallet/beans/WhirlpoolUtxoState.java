package com.samourai.whirlpool.client.wallet.beans;

import com.samourai.whirlpool.client.mix.listener.MixStep;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

public class WhirlpoolUtxoState {
  private WhirlpoolUtxoStatus status;
  private MixProgress mixProgress;
  private MixableStatus mixableStatus;

  private String message;
  private String error;

  private Long lastActivity;
  private Long lastError;
  private Subject<WhirlpoolUtxoState> observable;

  public WhirlpoolUtxoState(WhirlpoolUtxoStatus status) {
    this.status = status;
    this.mixProgress = null;
    this.mixableStatus = null;

    this.message = null;
    this.error = null;

    this.lastActivity = null;
    this.lastError = null;
    this.observable = BehaviorSubject.create();
  }

  private void emit() {
    // notify observers
    observable.onNext(this);
  }

  public WhirlpoolUtxoStatus getStatus() {
    return status;
  }

  public void setStatus(
      WhirlpoolUtxoStatus status,
      boolean updateLastActivity,
      MixProgress mixProgress,
      String error) {
    this.status = status;
    this.mixProgress = mixProgress;
    if (mixProgress != null) {
      String message = null;
      MixStep mixStep = mixProgress.getMixStep();
      if (mixStep != MixStep.SUCCESS) this.message = message;
    }
    this.error = error;
    if (error != null) {
      setLastError();
    }
    if (updateLastActivity) {
      setLastActivity();
    }
  }

  public void setStatus(
      WhirlpoolUtxoStatus status, boolean updateLastActivity, MixProgress mixProgress) {
    setStatus(status, updateLastActivity, mixProgress, null);
  }

  public void setStatus(WhirlpoolUtxoStatus status, boolean updateLastActivity, String error) {
    setStatus(status, updateLastActivity, null, error);
  }

  public void setStatus(WhirlpoolUtxoStatus status, boolean updateLastActivity) {
    setStatus(status, updateLastActivity, null, null);
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

  public boolean hasMessage() {
    return message != null;
  }

  public String getMessage() {
    return message;
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
