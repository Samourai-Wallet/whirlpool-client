package com.samourai.http.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.api.backend.beans.HttpException;
import io.reactivex.Observable;
import java.util.Map;
import java.util.concurrent.Callable;
import java8.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JacksonHttpClient implements IHttpClient {
  private static final Logger log = LoggerFactory.getLogger(JacksonHttpClient.class);

  private ObjectMapper objectMapper;

  public JacksonHttpClient() {
    this.objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  protected abstract String requestJsonGet(String urlStr, Map<String, String> headers)
      throws Exception;

  protected abstract String requestJsonPost(
      String urlStr, Map<String, String> headers, String jsonBody) throws Exception;

  protected abstract String requestJsonPostUrlEncoded(
      String urlStr, Map<String, String> headers, Map<String, String> body) throws Exception;

  protected void onRequestError(Exception e) {}

  @Override
  public <T> T getJson(String urlStr, Class<T> responseType, Map<String, String> headers)
      throws HttpException {
    if (log.isDebugEnabled()) {
      log.debug("getJson: " + urlStr);
    }
    try {
      String responseContent = requestJsonGet(urlStr, headers);
      T result = parseJson(responseContent, responseType);
      return result;
    } catch (Exception e) {
      onRequestError(e);
      if (log.isDebugEnabled()) {
        log.error("getJson failed: " + urlStr + ":" + e.getMessage());
      }
      if (!(e instanceof HttpException)) {
        e = new HttpException(e, null);
      }
      throw (HttpException) e;
    }
  }

  @Override
  public <T> Observable<Optional<T>> postJson(
      final String urlStr,
      final Class<T> responseType,
      final Map<String, String> headers,
      final Object bodyObj) {
    if (log.isDebugEnabled()) {
      log.debug("postJson: " + urlStr);
    }
    return httpObservable(
        new Callable<T>() {
          @Override
          public T call() throws Exception {
            try {
              String jsonBody = objectMapper.writeValueAsString(bodyObj);
              String responseContent = requestJsonPost(urlStr, headers, jsonBody);
              T result = parseJson(responseContent, responseType);
              return result;
            } catch (Exception e) {
              onRequestError(e);
              if (log.isDebugEnabled()) {
                log.error("postJson failed: " + urlStr, e);
              }
              throw e;
            }
          }
        });
  }

  @Override
  public <T> T postUrlEncoded(
      String urlStr, Class<T> responseType, Map<String, String> headers, Map<String, String> body)
      throws HttpException {
    if (log.isDebugEnabled()) {
      log.debug(
          "postUrlEncoded: " + urlStr + ", POST.body=" + (body != null ? body.keySet() : "null"));
    }
    try {
      String responseContent = requestJsonPostUrlEncoded(urlStr, headers, body);
      T result = parseJson(responseContent, responseType);
      return result;
    } catch (Exception e) {
      onRequestError(e);
      if (log.isDebugEnabled()) {
        log.error("postUrlEncoded failed: " + urlStr, e);
      }
      if (!(e instanceof HttpException)) {
        e = new HttpException(e, null);
      }
      throw (HttpException) e;
    }
  }

  private <T> T parseJson(String responseContent, Class<T> responseType) throws Exception {
    T result;
    if (log.isTraceEnabled()) {
      String responseStr =
          (responseContent != null
              ? responseContent.substring(0, Math.min(responseContent.length(), 50))
              : "null");
      log.trace(
          "response["
              + (responseType != null ? responseType.getCanonicalName() : "null")
              + "]: "
              + responseStr);
    }
    if (String.class.equals(responseType)) {
      result = (T) responseContent;
    } else {
      result = objectMapper.readValue(responseContent, responseType);
    }
    return result;
  }

  protected <T> Observable<Optional<T>> httpObservable(final Callable<T> supplier) {
    return Observable.fromCallable(
        new Callable<Optional<T>>() {
          @Override
          public Optional<T> call() throws Exception {
            try {
              return Optional.ofNullable(supplier.call());
            } catch (Exception e) {
              if (!(e instanceof HttpException)) {
                e = new HttpException(e, null);
              }
              throw e;
            }
          }
        });
  }

  protected ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
