package com.samourai.stomp.client;

import com.samourai.whirlpool.client.utils.MessageErrorListener;
import java.util.Map;

public interface IStompClient {
  void connect(
      String url,
      Map<String, String> stompHeaders,
      MessageErrorListener<IStompMessage, Throwable> onConnectOnDisconnectListener);

  String getSessionId();

  void subscribe(
      Map<String, String> stompHeaders,
      MessageErrorListener<IStompMessage, String> onMessageOnErrorListener);

  void send(Map<String, String> stompHeaders, Object payload);

  void disconnect();

  IStompClient copyForNewClient();
}
