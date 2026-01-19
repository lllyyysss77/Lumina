package com.lumina.logging;

import com.lumina.entity.RequestLog;
import com.lumina.service.RequestLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LogWriter {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Autowired
    private RequestLogService requestLogService;

    public void submit(RequestLogContext ctx) {
        executor.submit(() -> {
            try {
                RequestLog log = convert(ctx);
                requestLogService.save(log);
            } catch (Exception e) {
                log.error("日志写入失败: {}", e.getMessage());
            }
        });
    }

    private RequestLog convert(RequestLogContext ctx) {
        RequestLog log = new RequestLog();
        log.setId(ctx.getId());
        log.setRequestId(ctx.getRequestId());
        log.setRequestTime(ctx.getRequestTime());
        log.setRequestType(ctx.getRequestType());
        log.setRequestModelName(ctx.getRequestModel());
        log.setActualModelName(ctx.getActualModel());
        log.setProviderId(ctx.getProviderId());
        log.setProviderName(ctx.getProviderName());
        log.setIsStream(ctx.getStream());
        log.setInputTokens(ctx.getInputTokens());
        log.setOutputTokens(ctx.getOutputTokens());
        log.setFirstTokenTime(ctx.getFirstTokenTime());
        log.setFirstTokenMs(ctx.getFirstTokenMs());
        log.setTotalTime(ctx.getTotalTime());
        log.setTotalTimeMs(ctx.getTotalTimeMs());
        log.setCost(ctx.getCost());
        log.setStatus(ctx.getStatus());
        log.setErrorStage(ctx.getErrorStage());
        log.setErrorMessage(ctx.getErrorMessage());
        log.setRetryCount(ctx.getRetryCount());
        log.setRequestContent(ctx.getRequestContent());
        log.setResponseContent(ctx.getResponseContent());
        return log;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
