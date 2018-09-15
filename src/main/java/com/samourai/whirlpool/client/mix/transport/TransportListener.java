package com.samourai.whirlpool.client.mix.transport;

public interface TransportListener {

    void onTransportConnected(String stompUsername);
    void onTransportDisconnected(Throwable exception);

}
