package com.samourai.whirlpool.client.exception;

import org.glassfish.grizzly.http.util.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotifiableException extends Exception {
  private static final Logger log = LoggerFactory.getLogger(NotifiableException.class);
  private static final int STATUS_DEFAULT = HttpStatus.INTERNAL_SERVER_ERROR_500.getStatusCode();

  private int status;

  public NotifiableException(String message) {
    this(message, STATUS_DEFAULT);
  }

  public NotifiableException(HttpStatus status) {
    this(status.getReasonPhrase(), status.getStatusCode());
  }

  public NotifiableException(String message, int status) {
    super(message);
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
    return new NotifiableException("Error");
  }
}
