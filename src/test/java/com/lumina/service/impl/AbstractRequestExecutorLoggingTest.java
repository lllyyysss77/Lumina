package com.lumina.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.logging.LogWriter;
import com.lumina.logging.RequestLogContext;
import com.lumina.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AbstractRequestExecutorLoggingTest {

    private TestRequestExecutor executor;
    private LogWriter logWriter;

    @BeforeEach
    void setUp() {
        executor = new TestRequestExecutor();
        logWriter = mock(LogWriter.class);
        executor.logWriter = logWriter;
        executor.snowflakeIdGenerator = new SnowflakeIdGenerator(0, 0);
    }

    @Test
    void createLogContextRecordsProcessingLogImmediately() {
        ObjectNode request = executor.objectMapper.createObjectNode();
        request.put("model", "gpt-test");
        ModelGroupConfigItem provider = new ModelGroupConfigItem();
        provider.setProviderId(1L);
        provider.setProviderName("anyrouter");
        provider.setModelName("gpt-5.5");

        RequestLogContext ctx = executor.createLogContext(
                request,
                provider,
                "openai_chat_completions",
                true,
                Map.of(
                        "_lumina_api_key", "sk-test",
                        "_lumina_request_ip", "127.0.0.1",
                        "_lumina_request_model", "client-facing-group"
                )
        );

        ArgumentCaptor<RequestLogContext> captor = ArgumentCaptor.forClass(RequestLogContext.class);
        verify(logWriter).recordStart(captor.capture());
        assertEquals(ctx, captor.getValue());
        assertEquals("PROCESSING", ctx.getStatus());
        assertEquals("anyrouter", ctx.getProviderName());
        assertEquals("client-facing-group", ctx.getRequestModel());
        assertEquals("gpt-5.5", ctx.getActualModel());
    }

    @Test
    void createLogContextFallsBackToBodyModelWhenOriginalRequestModelIsMissing() {
        ObjectNode request = executor.objectMapper.createObjectNode();
        request.put("model", "body-model");
        ModelGroupConfigItem provider = new ModelGroupConfigItem();
        provider.setProviderId(1L);
        provider.setProviderName("anyrouter");
        provider.setModelName("provider-model");

        RequestLogContext ctx = executor.createLogContext(
                request,
                provider,
                "openai_chat_completions",
                false,
                Map.of()
        );

        assertEquals("body-model", ctx.getRequestModel());
        assertEquals("provider-model", ctx.getActualModel());
    }

    @Test
    void streamCancelAfterFirstTokenRecordsSuccessOnce() {
        RequestLogContext ctx = context();
        ctx.getFirstTokenArrived().set(true);
        ctx.getResponseBuffer().append("{\"delta\":\"hello\"}");

        executor.recordStreamCancel(ctx);
        executor.recordSuccess(ctx, "complete");
        executor.recordError(ctx, new RuntimeException("late error"));

        verify(logWriter, times(1)).submit(any(RequestLogContext.class));
        assertEquals("SUCCESS", ctx.getStatus());
        assertEquals("{\"delta\":\"hello\"}", ctx.getResponseContent());
    }

    @Test
    void streamCancelBeforeFirstTokenRecordsClientFailureOnce() {
        RequestLogContext ctx = context();

        executor.recordStreamCancel(ctx);
        executor.recordError(ctx, new RuntimeException("late error"));

        verify(logWriter, times(1)).submit(any(RequestLogContext.class));
        assertEquals("FAIL", ctx.getStatus());
        assertEquals("CLIENT", ctx.getErrorStage());
    }

    private RequestLogContext context() {
        RequestLogContext ctx = new RequestLogContext();
        ctx.setStartNano(System.nanoTime());
        return ctx;
    }

    private static class TestRequestExecutor extends AbstractRequestExecutor {
        @Override
        public boolean supports(String type) {
            return true;
        }

        @Override
        public Mono<ObjectNode> executeNormal(ObjectNode request, ModelGroupConfigItem provider,
                                              Map<String, String> queryParams, String modelAction,
                                              String type, Integer timeoutMs) {
            return Mono.empty();
        }

        @Override
        public Flux<ServerSentEvent<String>> executeStream(ObjectNode request, ModelGroupConfigItem provider,
                                                           Map<String, String> queryParams, String modelAction,
                                                           String type, Integer timeoutMs) {
            return Flux.empty();
        }
    }
}
