package com.lumina.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface LlmRequestExecutor {
    
    boolean supports(String type);

    Mono<ObjectNode> executeNormal(
            ObjectNode request,
            ModelGroupConfigItem provider,
            Map<String, String> queryParams,
            String modelAction,
            String type,
            Integer timeoutMs
    );

    Flux<ServerSentEvent<String>> executeStream(
            ObjectNode request,
            ModelGroupConfigItem provider,
            Map<String, String> queryParams,
            String modelAction,
            String type,
            Integer timeoutMs
    );
}