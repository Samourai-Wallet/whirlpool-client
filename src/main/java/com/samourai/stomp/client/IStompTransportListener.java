package com.samourai.stomp.client;

public interface IStompTransportListener {

    void onTransportConnected(String stompUsername);
    void onTransportDisconnected(Throwable exception);

}
