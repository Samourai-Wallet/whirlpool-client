package com.samourai.http.client;

import java.util.Map;

public interface IHttpClient {
  <T> T parseJson(String url, Class<T> entityClass) throws HttpException;

  void postJsonOverTor(String url, Object body) throws HttpException;

  void postUrlEncoded(String url, Map<String, String> body) throws HttpException;
}
