package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.entity.LlmModel;
import com.lumina.logging.LogWriter;
import com.lumina.logging.RequestLogContext;
import com.lumina.service.LlmModelService;
import com.lumina.service.LlmRequestExecutor;
import com.lumina.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

@Slf4j
public abstract class AbstractRequestExecutor implements LlmRequestExecutor {

    @Autowired
    protected SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    protected LogWriter logWriter;

    @Autowired
    protected LlmModelService llmModelService;

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
        if (node == null) return;

        // 提取 model 字段作为 actualModel
        if (!node.findValuesAsText("model").isEmpty()) {
            ctx.setActualModel(node.findValuesAsText("model").get(0));
        } else if (!node.findValuesAsText("modelVersion").isEmpty()) {
            ctx.setActualModel(node.findValuesAsText("modelVersion").get(0));
        }

        // 1. 处理标准的 "usage" 字段 (OpenAI, Anthropic message_delta, Anthropic normal)
        if (node.has("usage")) {
            parseUsageNode(ctx, node.get("usage"));
        }

        // 2. 处理 Anthropic message_start 中的 nested usage
        if (node.has("message") && node.get("message").has("usage")) {
            parseUsageNode(ctx, node.get("message").get("usage"));
        }

        // 3. 处理 Gemini 的 "usageMetadata" 字段
        if (node.has("usageMetadata")) {
            JsonNode usage = node.get("usageMetadata");
            if (usage.has("promptTokenCount") && (ctx.getInputTokens() == null || ctx.getInputTokens() == 0)) {
                ctx.setInputTokens(usage.get("promptTokenCount").asInt());
            }
            if (usage.has("candidatesTokenCount") && (ctx.getOutputTokens() == null || ctx.getOutputTokens() == 0)) {
                ctx.setOutputTokens(usage.get("candidatesTokenCount").asInt());
            }
        }

        // 4. 处理 OpenAI /v1/responses 中的 nested usage
        if (node.has("response") && node.get("response").has("usage")) {
            parseUsageNode(ctx, node.get("response").get("usage"));
        }
    }

    private void parseUsageNode(RequestLogContext ctx, JsonNode usage) {
        // 兼容旧版 OpenAI 字段
        if (usage.has("prompt_tokens") && (ctx.getInputTokens() == null || ctx.getInputTokens() == 0)) {
            ctx.setInputTokens(usage.get("prompt_tokens").asInt());
        }
        if (usage.has("completion_tokens") && (ctx.getOutputTokens() == null || ctx.getOutputTokens() == 0)) {
            ctx.setOutputTokens(usage.get("completion_tokens").asInt());
        }

        // 兼容新版 /responses 接口字段 或 Anthropic 字段
        if (usage.has("input_tokens") && (ctx.getInputTokens() == null || ctx.getInputTokens() == 0)) {
            ctx.setInputTokens(usage.get("input_tokens").asInt());
        }
        if (usage.has("output_tokens") && (ctx.getOutputTokens() == null || ctx.getOutputTokens() == 0)) {
            ctx.setOutputTokens(usage.get("output_tokens").asInt());
        }
    }

    protected void calculateCost(RequestLogContext ctx) {
        if (ctx.getRequestModel() == null) {
            return;
        }

        try {
            LlmModel model = llmModelService.getOne(new LambdaQueryWrapper<LlmModel>()
                    .eq(LlmModel::getModelName, ctx.getActualModel())
                    .orderByDesc(LlmModel::getInputPrice)
                    .last("limit 1"));
            if (model == null) {
                log.warn("未找到模型价格信息: {}", ctx.getActualModel());
                return;
            }

            BigDecimal inputCost = BigDecimal.ZERO;
            BigDecimal outputCost = BigDecimal.ZERO;

            // 计算输入费用（价格单位为每百万Token）
            if (ctx.getInputTokens() != null && ctx.getInputTokens() > 0) {
                inputCost = model.getInputPrice()
                        .multiply(BigDecimal.valueOf(ctx.getInputTokens()))
                        .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
            }

            // 计算输出费用（价格单位为每百万Token）
            if (ctx.getOutputTokens() != null && ctx.getOutputTokens() > 0) {
                outputCost = model.getOutputPrice()
                        .multiply(BigDecimal.valueOf(ctx.getOutputTokens()))
                        .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
            }

            // 总费用保留4位小数
            BigDecimal totalCost = inputCost.add(outputCost).setScale(4, RoundingMode.HALF_UP);
            ctx.setCost(totalCost);
        } catch (Exception e) {
            log.error("计算费用失败: model={}, error={}", ctx.getActualModel(), e.getMessage());
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
        calculateCost(ctx);
        logWriter.submit(ctx);
    }

    protected WebClient createWebClient(ModelGroupConfigItem provider) {
        String apiKey = provider.getApiKey();
        String authHeader = apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
        return WebClient.builder()
                .baseUrl(provider.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .build();
    }

    /**
     * 应用超时到 Mono，如果 timeoutMs 有效
     */
    protected <T> Mono<T> applyTimeout(Mono<T> mono, Integer timeoutMs) {
        if (timeoutMs != null && timeoutMs > 0) {
            return mono.timeout(Duration.ofMillis(timeoutMs));
        }
        return mono;
    }

    /**
     * 应用超时到 Flux，如果 timeoutMs 有效
     */
    protected <T> Flux<T> applyTimeout(Flux<T> flux, Integer timeoutMs) {
        if (timeoutMs != null && timeoutMs > 0) {
            return flux.timeout(Duration.ofMillis(timeoutMs));
        }
        return flux;
    }
}
