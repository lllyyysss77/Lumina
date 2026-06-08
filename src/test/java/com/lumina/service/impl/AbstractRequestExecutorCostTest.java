package com.lumina.service.impl;

import com.lumina.entity.LlmModel;
import com.lumina.logging.RequestLogContext;
import com.lumina.service.LlmModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractRequestExecutorCostTest {

    private OpenAiRequestExecutor executor;
    private LlmModelService llmModelService;

    @BeforeEach
    void setUp() {
        executor = new OpenAiRequestExecutor();
        llmModelService = mock(LlmModelService.class);
        executor.llmModelService = llmModelService;
    }

    @Test
    void calculatesOpenAiCacheTokensWithCachePricesWithoutDoubleChargingInput() {
        when(llmModelService.findLatestByModelName("gpt-test")).thenReturn(model(
                "10.00", "20.00", "1.00", "12.00"));

        RequestLogContext ctx = context("openai_chat_completions", 1000, 500, 400, 100);

        executor.calculateCost(ctx);

        assertEquals(new BigDecimal("0.0166"), ctx.getCost());
    }

    @Test
    void calculatesAnthropicCacheTokensAsSeparateInputBuckets() {
        when(llmModelService.findLatestByModelName("gpt-test")).thenReturn(model(
                "10.00", "20.00", "1.00", "12.00"));

        RequestLogContext ctx = context("anthropic_messages", 1000, 500, 400, 100);

        executor.calculateCost(ctx);

        assertEquals(new BigDecimal("0.0216"), ctx.getCost());
    }

    @Test
    void fallsBackToInputPriceWhenCachePricesAreMissing() {
        when(llmModelService.findLatestByModelName("gpt-test")).thenReturn(model(
                "10.00", "0.00", null, null));

        RequestLogContext ctx = context("openai_responses", 1000, 0, 400, 100);

        executor.calculateCost(ctx);

        assertEquals(new BigDecimal("0.0100"), ctx.getCost());
    }

    @Test
    void doesNotForwardInternalLuminaQueryParams() {
        org.springframework.web.util.UriComponentsBuilder builder =
                org.springframework.web.util.UriComponentsBuilder.fromPath("/v1/images/generations");

        executor.applyQueryParams(builder, Map.of(
                "_lumina_api_key", "sk-client",
                "_lumina_request_ip", "127.0.0.1",
                "_lumina_request_model", "client-facing-group",
                "_lumina_protocol_conversion", "OPENAI_IMAGES_TO_OPENAI_CHAT",
                "api-version", "2024-02-01"
        ));

        assertEquals("/v1/images/generations?api-version=2024-02-01", builder.build().toUriString());
    }

    private RequestLogContext context(String requestType, int inputTokens, int outputTokens,
                                      int cacheReadTokens, int cacheCreationTokens) {
        RequestLogContext ctx = new RequestLogContext();
        ctx.setRequestModel("gpt-test");
        ctx.setActualModel("gpt-test");
        ctx.setRequestType(requestType);
        ctx.setInputTokens(inputTokens);
        ctx.setOutputTokens(outputTokens);
        ctx.setCacheReadTokens(cacheReadTokens);
        ctx.setCacheCreationTokens(cacheCreationTokens);
        return ctx;
    }

    private LlmModel model(String inputPrice, String outputPrice, String cacheReadPrice, String cacheWritePrice) {
        LlmModel model = new LlmModel();
        model.setInputPrice(new BigDecimal(inputPrice));
        model.setOutputPrice(new BigDecimal(outputPrice));
        if (cacheReadPrice != null) {
            model.setCacheReadPrice(new BigDecimal(cacheReadPrice));
        }
        if (cacheWritePrice != null) {
            model.setCacheWritePrice(new BigDecimal(cacheWritePrice));
        }
        return model;
    }
}
