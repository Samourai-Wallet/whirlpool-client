package com.samourai.whirlpool.client.whirlpool.httpClient;

public class WhirlpoolHttpException extends Exception {
    private String responseBody;

    public WhirlpoolHttpException(Exception cause, String responseBody) {
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
