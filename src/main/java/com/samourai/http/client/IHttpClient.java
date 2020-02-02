package com.samourai.http.client;

import com.samourai.wallet.api.backend.IBackendClient;
import io.reactivex.Observable;
import java.util.Map;
import java8.util.Optional;

public interface IHttpClient extends IBackendClient {
  <T> Observable<Optional<T>> postJson(
      String url, Class<T> responseType, Map<String, String> headers, Object body);

  <T> Observable<Optional<T>> postJsonOverTor(
      String url, Class<T> responseType, Map<String, String> headers, Object body);
}
