package com.samourai.http.client;

public interface IHttpClientService {
  IHttpClient getHttpClient(HttpUsage httpUsage);
}
