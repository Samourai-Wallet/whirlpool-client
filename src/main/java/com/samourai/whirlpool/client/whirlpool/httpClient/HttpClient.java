package com.samourai.whirlpool.client.whirlpool.httpClient;

public interface HttpClient {
    <T> T getForEntity(String url, Class<T> entityClass) throws HttpException;
    void postOverTor(String url, Object body);
}
