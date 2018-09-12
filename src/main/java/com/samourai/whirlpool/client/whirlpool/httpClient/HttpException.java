package com.samourai.whirlpool.client.whirlpool.httpClient;

public class HttpException extends Exception {
    private String responseBody;

    public HttpException(Exception e) {
        super(e);
        this.responseBody = responseBody;
    }

    public HttpException(Exception cause, String responseBody) {
        super(cause);
        this.responseBody = responseBody;
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    public String getResponseBody() {
        return responseBody;
    }
}
