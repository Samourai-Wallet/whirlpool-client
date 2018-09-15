package com.samourai.whirlpool.client.mix.transport;

import javax.websocket.MessageHandler;
import java.util.Map;

public interface IWhirlpoolStompClient {
    void connect(String url, Map<String,String> stompHeaders, MessageHandler.Whole<String> onConnect, MessageHandler.Whole<Throwable> onDisconnect);
    String getSessionId();
    void subscribe(Map<String,String> stompHeaders, MessageHandler.Whole<Object> onMessage, MessageHandler.Whole<String> onError);
    void send(Map<String,String> stompHeaders, Object payload);
    void disconnect();
}
