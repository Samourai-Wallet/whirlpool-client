package com.samourai.whirlpool.client.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotifiableException extends Exception {
  private static final Logger log = LoggerFactory.getLogger(NotifiableException.class);
  private static final int STATUS_DEFAULT = 500;

  private int status;

  public NotifiableException(String message, Exception cause) {
    this(message, cause, STATUS_DEFAULT);
  }

  public NotifiableException(String message) {
    this(message, null, STATUS_DEFAULT);
  }

  public NotifiableException(String message, int status) {
    this(message, null, status);
  }

  public NotifiableException(String message, Exception cause, int status) {
    super(message, cause);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }

  public static NotifiableException computeNotifiableException(Exception e) {
    if (NotifiableException.class.isAssignableFrom(e.getClass())) {
      return (NotifiableException) e;
    }
    log.warn("Exception obfuscated to user", e);
    return new NotifiableException("Technical error, check logs for details");
  }
}
