package com.lumina.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.logging.RequestLogContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class GeminiModelsExecutor extends AbstractRequestExecutor {

    @Override
    public boolean supports(String type) {
        return "gemini_models".equals(type);
    }

    @Override
    public Mono<ObjectNode> executeNormal(ObjectNode request, ModelGroupConfigItem provider, Map<String, String> queryParams, String modelAction, String type, Integer timeoutMs) {
        RequestLogContext ctx = createLogContext(request, provider, type, false);
        return createWebClient(provider).post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v1beta/models" + (!modelAction.isEmpty() ? "/" + modelAction : ""));
                    queryParams.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ObjectNode.class)
                .doOnNext(resp -> {
                    handleUsage(ctx, resp);
                    recordSuccess(ctx, resp.toString());
                })
                .doOnError(err -> recordError(ctx, err));
    }

    @Override
    public Flux<ServerSentEvent<String>> executeStream(ObjectNode request, ModelGroupConfigItem provider, Map<String, String> queryParams, String modelAction, String type, Integer timeoutMs) {
        RequestLogContext ctx = createLogContext(request, provider, type, true);
        return createWebClient(provider).post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/v1beta/models" + (!modelAction.isEmpty() ? "/" + modelAction : ""));
                    queryParams.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .log("SSE-FLOW")
                .doOnNext(event -> {
                    String data = event.data();
                    if (data == null) return;
                    if (ctx.getFirstTokenArrived().compareAndSet(false, true)) {
                        ctx.setFirstTokenMs((int) ((System.nanoTime() - ctx.getStartNano()) / 1_000_000));
                    }
                    ctx.getResponseBuffer().append(data);

                    try {
                        handleUsage(ctx, objectMapper.readTree(data));
                    } catch (Exception ignored) {
                    }
                })
                .doOnError(err -> recordError(ctx, err))
                .doOnComplete(() -> recordSuccess(ctx, ctx.getResponseBuffer().toString()));
    }
}