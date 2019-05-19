package com.samourai.http.client;

import com.samourai.wallet.api.backend.IBackendClient;
import com.samourai.wallet.api.backend.beans.HttpException;
import java.util.Map;

public interface IHttpClient extends IBackendClient {
  <T> T parseJson(String url, Class<T> entityClass) throws HttpException;

  void postJsonOverTor(String url, Object body) throws HttpException;

  void postUrlEncoded(String url, Map<String, String> body) throws HttpException;
}
