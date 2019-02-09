package com.samourai.stomp.client;

import java.util.Map;
import javax.websocket.MessageHandler;

public interface IStompClient {
  void connect(
      String url,
      Map<String, String> stompHeaders,
      MessageHandler.Whole<IStompMessage> onConnect,
      MessageHandler.Whole<Throwable> onDisconnect);

  String getSessionId();

  void subscribe(
      Map<String, String> stompHeaders,
      MessageHandler.Whole<IStompMessage> onMessage,
      MessageHandler.Whole<String> onError);

  void send(Map<String, String> stompHeaders, Object payload);

  void disconnect();

  IStompClient copyForNewClient();
}
