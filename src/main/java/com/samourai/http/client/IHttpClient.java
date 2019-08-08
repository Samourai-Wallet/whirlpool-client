package com.samourai.http.client;

import com.samourai.wallet.api.backend.IBackendClient;
import com.samourai.wallet.api.backend.beans.HttpException;
import java.util.Map;

public interface IHttpClient extends IBackendClient {
  <T> T postJsonOverTor(String url, Class<T> responseType, Map<String, String> headers, Object body)
      throws HttpException;
}
