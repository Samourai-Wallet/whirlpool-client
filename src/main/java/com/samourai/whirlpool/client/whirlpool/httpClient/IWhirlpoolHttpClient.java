package com.samourai.whirlpool.client.whirlpool.httpClient;

public interface IWhirlpoolHttpClient {
    <T> T getJsonAsEntity(String url, Class<T> entityClass) throws WhirlpoolHttpException;
    void postJsonOverTor(String url, Object body) throws WhirlpoolHttpException;
}
