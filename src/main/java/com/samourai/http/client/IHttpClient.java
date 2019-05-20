package com.samourai.http.client;

import com.samourai.wallet.api.backend.IBackendClient;
import com.samourai.wallet.api.backend.beans.HttpException;

public interface IHttpClient extends IBackendClient {
  <T> T postJsonOverTor(String url, Class<T> responseType, Object body) throws HttpException;
}
