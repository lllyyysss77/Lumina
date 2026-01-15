package com.lumina.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.logging.LogWriter;
import com.lumina.logging.RequestLogContext;
import com.lumina.service.LlmRequestExecutor;
import com.lumina.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Slf4j
public abstract class AbstractRequestExecutor implements LlmRequestExecutor {

    @Autowired
    protected SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    protected LogWriter logWriter;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected RequestLogContext createLogContext(ObjectNode request, ModelGroupConfigItem provider, String type, boolean stream) {
        RequestLogContext ctx = new RequestLogContext();
        ctx.setId(String.valueOf(snowflakeIdGenerator.nextId()));
        ctx.setProviderId(provider.getProviderId());
        ctx.setProviderName(provider.getProviderName());
        ctx.setRequestId(UUID.randomUUID().toString());
        ctx.setStartNano(System.nanoTime());
        ctx.setRequestTime(System.currentTimeMillis() / 1000);
        ctx.setRequestType(type);
        ctx.setStream(stream);
        ctx.setRequestModel(provider.getModelName());
        ctx.setRequestContent(request.toPrettyString());
        return ctx;
    }

    protected void handleUsage(RequestLogContext ctx, JsonNode node) {
        if (node != null && node.has("usage")) {
            JsonNode usage = node.get("usage");
            // 兼容旧版 OpenAI 字段
            if (usage.has("prompt_tokens")) ctx.setInputTokens(usage.get("prompt_tokens").asInt());
            if (usage.has("completion_tokens")) ctx.setOutputTokens(usage.get("completion_tokens").asInt());
            
            // 兼容新版 /responses 接口字段
            if (usage.has("input_tokens") && ctx.getInputTokens() == null) {
                ctx.setInputTokens(usage.get("input_tokens").asInt());
            }
            if (usage.has("output_tokens") && ctx.getOutputTokens() == null) {
                ctx.setOutputTokens(usage.get("output_tokens").asInt());
            }
        }
    }

    protected void recordError(RequestLogContext ctx, Throwable err) {
        ctx.setStatus("FAIL");
        ctx.setErrorStage("HTTP");
        ctx.setErrorMessage(err.getMessage());
        ctx.setTotalTimeMs((int) ((System.nanoTime() - ctx.getStartNano()) / 1_000_000));
        logWriter.submit(ctx);
    }

    protected void recordSuccess(RequestLogContext ctx, String content) {
        ctx.setTotalTimeMs((int) ((System.nanoTime() - ctx.getStartNano()) / 1_000_000));
        ctx.setResponseContent(content);
        logWriter.submit(ctx);
    }

    protected WebClient createWebClient(ModelGroupConfigItem provider) {
        return WebClient.builder()
                .baseUrl(provider.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .build();
    }
}
