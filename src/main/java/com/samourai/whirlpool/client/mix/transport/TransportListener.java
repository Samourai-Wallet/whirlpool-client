package com.samourai.whirlpool.client.mix.transport;

public interface TransportListener {

    void onTransportConnectionLost(Throwable exception);

}
