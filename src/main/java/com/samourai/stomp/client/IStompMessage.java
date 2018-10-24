package com.samourai.stomp.client;

public interface IStompMessage {
  String getStompHeader(String headerName);

  Object getPayload();
}
