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

import java.util.Locale;
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
        prepareRequestForProvider(request, provider, type);
        log.info("[DEBUG-RELAY] 非流式转发，供应商：{}, 请求地址：{}, 模型：{}", provider.getProviderName(), provider.getBaseUrl(), provider.getModelName());
        log.info("[DEBUG-RELAY] 转发请求头: Authorization={}, providerType={}",
                provider.getApiKey() != null ? "Bearer " + provider.getApiKey().substring(0, Math.min(8, provider.getApiKey().length())) + "..." : "null",
                provider.getProviderType());
        log.info("[DEBUG-RELAY] 转发请求体: {}", request.toString());
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
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("[DEBUG-RELAY] 上游错误响应 {} from {}: {}",
                                            response.statusCode(), provider.getBaseUrl(), body);
                                    return Mono.error(new RuntimeException(
                                            "HTTP " + response.statusCode() + " from " + provider.getBaseUrl() + ": " + body));
                                }))
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
        prepareRequestForProvider(request, provider, type);
        log.info("调用流式接口，供应商：{},请求地址：{},模型：{}", provider.getProviderName(), provider.getBaseUrl(), provider.getModelName());
        log.info("[DEBUG-RELAY] 转发请求头: Authorization={}, providerType={}",
                provider.getApiKey() != null ? "Bearer " + provider.getApiKey().substring(0, Math.min(8, provider.getApiKey().length())) + "..." : "null",
                provider.getProviderType());
        log.info("[DEBUG-RELAY] 转发请求体: {}", request.toString());
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
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("[DEBUG-RELAY] 上游错误响应(流式) {} from {}: {}",
                                            response.statusCode(), provider.getBaseUrl(), body);
                                    return Mono.error(new RuntimeException(
                                            "HTTP " + response.statusCode() + " from " + provider.getBaseUrl() + ": " + body));
                                }))
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

    static void prepareRequestForProvider(ObjectNode request, ModelGroupConfigItem provider, String type) {
        if (request == null || provider == null || !"openai_chat_completions".equals(type)) {
            return;
        }
        if (request.has("thinking") || !isDeepSeekRequest(request, provider) || !hasAssistantHistoryMissingReasoning(request)) {
            return;
        }

        ObjectNode thinking = request.putObject("thinking");
        thinking.put("type", "disabled");
        log.info("[DEBUG-RELAY] DeepSeek assistant history is missing reasoning_content; disabling thinking mode for this request.");
    }

    private static boolean isDeepSeekRequest(ObjectNode request, ModelGroupConfigItem provider) {
        return containsIgnoreCase(provider.getBaseUrl(), "deepseek")
                || containsIgnoreCase(provider.getProviderName(), "deepseek")
                || containsIgnoreCase(provider.getModelName(), "deepseek")
                || containsIgnoreCase(request.path("model").asText(""), "deepseek");
    }

    private static boolean hasAssistantHistoryMissingReasoning(ObjectNode request) {
        JsonNode messages = request.get("messages");
        if (messages == null || !messages.isArray()) {
            return false;
        }

        for (JsonNode message : messages) {
            if (!"assistant".equals(message.path("role").asText())) {
                continue;
            }
            if (!hasAssistantHistoryPayload(message)) {
                continue;
            }
            JsonNode reasoningContent = message.get("reasoning_content");
            if (reasoningContent == null || reasoningContent.isNull() || reasoningContent.asText().isBlank()) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAssistantHistoryPayload(JsonNode message) {
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
            return true;
        }
        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return false;
        }
        if (content.isTextual()) {
            return !content.asText().isBlank();
        }
        return true;
    }

    private static boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expected);
    }
}
