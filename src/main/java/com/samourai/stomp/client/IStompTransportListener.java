package com.samourai.stomp.client;

public interface IStompTransportListener {

  void onTransportConnected();

  void onTransportDisconnected(Throwable exception);
}
