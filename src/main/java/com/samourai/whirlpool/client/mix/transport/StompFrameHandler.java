package com.samourai.whirlpool.client.mix.transport;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaders;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.Consumer;

public class StompFrameHandler implements org.springframework.messaging.simp.stomp.StompFrameHandler {
    private Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Consumer<Object> frameHandler;
    private final Consumer<Object> errorHandler;

    public StompFrameHandler(Consumer<Object> frameHandler, Consumer<Object> errorHandler) {
        this.frameHandler = frameHandler;
        this.errorHandler = errorHandler;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        String messageType = headers.get(WhirlpoolProtocol.HEADER_MESSAGE_TYPE).get(0);
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
        String protocolVersion = headers.getFirst(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION);
        if (!WhirlpoolProtocol.PROTOCOL_VERSION.equals(protocolVersion)) {
            String errorMessage = "Version mismatch: server=" + (protocolVersion != null ? protocolVersion : "unknown") + ", client=" + WhirlpoolProtocol.PROTOCOL_VERSION;
            errorHandler.accept(errorMessage);
        }
        else {
            frameHandler.accept(payload);
        }
    }
}
