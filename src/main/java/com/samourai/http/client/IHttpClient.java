package com.samourai.http.client;

import com.samourai.wallet.api.backend.IBackendClient;
import io.reactivex.Observable;
import java.util.Map;
import java8.util.Optional;

public interface IHttpClient extends IBackendClient {
  void connect() throws Exception;

  <T> Observable<Optional<T>> postJson(
      String url, Class<T> responseType, Map<String, String> headers, Object body);
}
