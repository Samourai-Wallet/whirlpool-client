package com.samourai.http.client;

import com.samourai.wallet.api.backend.IBackendClient;
import com.samourai.wallet.api.backend.beans.HttpException;
import io.reactivex.Observable;
import java.util.Map;

public interface IHttpClient extends IBackendClient {
  <T> Observable<T> postJsonOverTor(
      String url, Class<T> responseType, Map<String, String> headers, Object body)
      throws HttpException;
}
