package com.samourai.http.client;

public interface IHttpClient {
    <T> T parseJson(String url, Class<T> entityClass) throws HttpException;
    void postJsonOverTor(String url, Object body) throws HttpException;
}
