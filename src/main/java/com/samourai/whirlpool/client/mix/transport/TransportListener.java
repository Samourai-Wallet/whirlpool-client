package com.samourai.whirlpool.client.mix.transport;

public interface TransportListener {

    void onTransportConnected(String stompUsername);
    void onTransportConnectionLost(Throwable exception);

}
