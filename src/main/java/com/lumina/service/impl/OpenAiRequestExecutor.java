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
            "openai_chat_completions", "/chat/completions",
            "openai_responses", "/responses"
    );

    @Override
    public boolean supports(String type) {
        return URI_MAP.containsKey(type);
    }

    @Override
    public Mono<ObjectNode> executeNormal(ObjectNode request, ModelGroupConfigItem provider, Map<String, String> queryParams, String type) {
        RequestLogContext ctx = createLogContext(request, provider, type, false);
        return createWebClient(provider).post()
                .uri(uriBuilder -> {
                    uriBuilder.path(URI_MAP.get(type));
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
    public Flux<ServerSentEvent<String>> executeStream(ObjectNode request, ModelGroupConfigItem provider, Map<String, String> queryParams, String type) {
        RequestLogContext ctx = createLogContext(request, provider, type, true);
        return createWebClient(provider).post()
                .uri(uriBuilder -> {
                    uriBuilder.path(URI_MAP.get(type));
                    queryParams.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request).retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .doOnNext(event -> {
                    System.out.println("[SSE] raw event: {}"+ event);
                    String data = event.data();
                    if (data == null) return;
                    if (ctx.getFirstTokenArrived().compareAndSet(false, true)) {
                        ctx.setFirstTokenMs((int) ((System.nanoTime() - ctx.getStartNano()) / 1_000_000));
                    }
                    if (!"[DONE]".equals(data)) {
                        ctx.getResponseBuffer().append(data);
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            // 尝试解析根路径的 usage (Chat Completions)
                            handleUsage(ctx, chunk);
                            // 尝试解析嵌套在 response 里的 usage (/v1/responses)
                            if (chunk.has("response")) {
                                handleUsage(ctx, chunk.get("response"));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                })
                .doOnError(err -> recordError(ctx, err))
                .doOnComplete(() -> recordSuccess(ctx, ctx.getResponseBuffer().toString()));
    }
}