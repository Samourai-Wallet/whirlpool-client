package com.samourai.whirlpool.client.mix.transport;

import java.util.Map;
import java.util.function.Consumer;

public interface StompClient {
    void connect(String url, Map<String,String> stompHeaders, Consumer<String> onConnect, Consumer<Exception> onDisconnect);
    String getSessionId();
    void subscribe(Map<String,String> stompHeaders, Consumer<Object> onMessage, Consumer<Object> onError);
    void send(Map<String,String> stompHeaders, Object payload);
    void disconnect();
}
