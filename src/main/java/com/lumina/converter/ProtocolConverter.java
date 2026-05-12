package com.lumina.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ProtocolConverter {

    ProtocolType sourceType();

    ProtocolType targetType();

    ObjectNode convertRequest(ObjectNode request);

    ObjectNode convertResponse(ObjectNode response);

    Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream);
}
