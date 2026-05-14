package com.lumina.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.logging.RequestLogContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
public class OpenAiRequestExecutor extends AbstractRequestExecutor {

    private static final Map<String, String> URI_MAP = Map.of(
            "openai_chat_completions", "/v1/chat/completions",
            "openai_responses", "/v1/responses"
    );

    @Override
    public boolean supports(String type) {
        return URI_MAP.containsKey(type);
    }

    @Override
    public Mono<ObjectNode> executeNormal(ObjectNode request, ModelGroupConfigItem provider, Map<String, String> queryParams, String modelAction, String type, Integer timeoutMs) {
        RequestLogContext ctx = createLogContext(request, provider, type, false, queryParams);
        Mono<ObjectNode> result = createWebClient(provider).post()
                .uri(uriBuilder -> {
                    uriBuilder.path(URI_MAP.get(type));
                    applyQueryParams(uriBuilder, queryParams);
                    return uriBuilder.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ObjectNode.class);

        return applyTimeout(result, timeoutMs)
                .doOnNext(resp -> {
                    handleUsage(ctx, resp);
                    recordSuccess(ctx, resp.toString());
                })
                .doOnError(err -> recordError(ctx, err));
    }

    @Override
    public Flux<ServerSentEvent<String>> executeStream(ObjectNode request, ModelGroupConfigItem provider, Map<String, String> queryParams, String modelAction, String type, Integer timeoutMs) {
        log.info("调用流式接口，供应商：{},请求地址：{},模型：{}", provider.getProviderName(), provider.getBaseUrl(), provider.getModelName());
        RequestLogContext ctx = createLogContext(request, provider, type, true, queryParams);
        Flux<ServerSentEvent<String>> result = createWebClient(provider).post()
                .uri(uriBuilder -> {
                    uriBuilder.path(URI_MAP.get(type));
                    applyQueryParams(uriBuilder, queryParams);
                    return uriBuilder.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request).retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

        return applyTimeout(result, timeoutMs)
                .doOnNext(event -> {
                    log.debug("[SSE] raw event: {}", event);
                    String data = event.data();
                    if (data == null) return;
                    if (ctx.getFirstTokenArrived().compareAndSet(false, true)) {
                        ctx.setFirstTokenMs((int) ((System.nanoTime() - ctx.getStartNano()) / 1_000_000));
                    }
                    if (!"[DONE]".equals(data)) {
                        appendResponseChunk(ctx, data);
                        try {
                            handleUsage(ctx, objectMapper.readTree(data));
                        } catch (Exception ignored) {
                        }
                    }
                })
                .onErrorResume(err -> {
                    recordError(ctx, err);
                    String errorMessage = "{\"error\": {\"message\": \"网关传输中途发生网络异常中断，请稍后重试。\"}}";
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data(errorMessage)
                            .build())
                            .concatWith(Flux.error(err));
                })
                .doOnComplete(() -> recordSuccess(ctx, ctx.getResponseBuffer().toString()));
    }
}
