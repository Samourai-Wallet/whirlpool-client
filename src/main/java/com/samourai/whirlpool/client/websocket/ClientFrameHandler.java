package com.samourai.whirlpool.client.websocket;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.Consumer;

public class ClientFrameHandler implements StompFrameHandler {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private WhirlpoolProtocol whirlpoolProtocol;
    private final Consumer<Object> frameHandler;

    public ClientFrameHandler(WhirlpoolProtocol whirlpoolProtocol, Consumer<Object> frameHandler) {
        this.whirlpoolProtocol = whirlpoolProtocol;
        this.frameHandler = frameHandler;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        String messageType = headers.get(whirlpoolProtocol.HEADER_MESSAGE_TYPE).get(0);
        try {
            return Class.forName(messageType);
        }
        catch(ClassNotFoundException e) {
            log.error("unknown message type: " + messageType, e);
            return null;
        }
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        frameHandler.accept(payload);
    }
}
