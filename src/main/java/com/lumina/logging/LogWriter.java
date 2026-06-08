package com.lumina.logging;

import com.lumina.config.LuminaProperties;
import com.lumina.entity.RequestLog;
import com.lumina.service.RequestLogService;
import com.lumina.stats.StatsAccumulator;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
public class LogWriter {

    private final RequestLogService requestLogService;
    private final LuminaProperties.Logging loggingProperties;
    private final MeterRegistry meterRegistry;
    private final StatsAccumulator statsAccumulator;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final LongAdder droppedLogs = new LongAdder();
    private final DistributionSummary batchSizeSummary;
    private final Timer flushTimer;

    private LinkedBlockingQueue<RequestLog> queue;

    public LogWriter(RequestLogService requestLogService, LuminaProperties luminaProperties,
                     MeterRegistry meterRegistry, StatsAccumulator statsAccumulator) {
        this.requestLogService = requestLogService;
        this.loggingProperties = luminaProperties.getLogging();
        this.meterRegistry = meterRegistry;
        this.statsAccumulator = statsAccumulator;
        this.batchSizeSummary = DistributionSummary.builder("lumina_log_batch_size")
                .description("Number of request logs written in a batch")
                .register(meterRegistry);
        this.flushTimer = Timer.builder("lumina_log_flush_duration")
                .description("Time spent flushing request logs")
                .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        this.queue = new LinkedBlockingQueue<>(loggingProperties.getQueueCapacity());
        Gauge.builder("lumina_log_queue_size", queue, LinkedBlockingQueue::size)
                .description("Current number of request logs waiting to be flushed")
                .register(meterRegistry);
        Gauge.builder("lumina_log_dropped_total", droppedLogs, LongAdder::sum)
                .description("Total number of request logs dropped because the queue was full")
                .register(meterRegistry);
        executor.scheduleWithFixedDelay(
                this::flushSafely,
                loggingProperties.getFlushIntervalMs(),
                loggingProperties.getFlushIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    public void recordStart(RequestLogContext ctx) {
        try {
            RequestLog logEntry = convert(ctx, false);
            logEntry.setRequestContent(null);
            logEntry.setResponseContent(null);
            requestLogService.save(logEntry);
        } catch (Exception e) {
            log.error("初始化请求日志失败: requestId={}, provider={}, model={}",
                    ctx.getRequestId(), ctx.getProviderName(), ctx.getRequestModel(), e);
        }
    }

    public void submit(RequestLogContext ctx) {
        RequestLog logEntry = convert(ctx, true);
        if (queue == null) {
            writeFinalLogImmediately(logEntry);
            return;
        }
        if (!queue.offer(logEntry)) {
            meterRegistry.counter("lumina_log_queue_full_events_total").increment();
            log.warn("请求日志最终态队列已满，切换为同步更新: requestId={}", logEntry.getRequestId());
            writeFinalLogImmediately(logEntry);
        }
    }

    private void writeFinalLogImmediately(RequestLog logEntry) {
        try {
            List<RequestLog> batch = List.of(logEntry);
            writeFinalLogs(batch);
        } catch (Exception e) {
            droppedLogs.increment();
            meterRegistry.counter("lumina_log_drop_events_total").increment();
            long dropped = droppedLogs.sum();
            if (dropped == 1 || dropped % 100 == 0) {
                log.warn("请求日志队列已满，累计丢弃 {} 条日志", dropped);
            }
            log.error("同步更新请求日志最终态失败: requestId={}", logEntry.getRequestId(), e);
        }
    }

    private void flushSafely() {
        flushTimer.record(() -> {
            try {
                flushBatch();
            } catch (Exception e) {
                log.error("批量写入请求日志失败", e);
            }
        });
    }

    private void flushBatch() {
        if (queue == null || queue.isEmpty()) {
            return;
        }

        List<RequestLog> batch = new ArrayList<>(loggingProperties.getBatchSize());
        queue.drainTo(batch, loggingProperties.getBatchSize());
        if (batch.isEmpty()) {
            return;
        }

        batchSizeSummary.record(batch.size());
        writeFinalLogs(batch);
    }

    private void writeFinalLogs(List<RequestLog> batch) {
        requestLogService.updateBatchLogs(batch);
        statsAccumulator.accumulate(batch);
    }

    private RequestLog convert(RequestLogContext ctx, boolean includePayloads) {
        RequestLog logEntry = new RequestLog();
        logEntry.setId(ctx.getId());
        logEntry.setRequestId(ctx.getRequestId());
        logEntry.setRequestTime(ctx.getRequestTime());
        logEntry.setRequestType(ctx.getRequestType());
        logEntry.setRequestModelName(ctx.getRequestModel());
        logEntry.setActualModelName(ctx.getActualModel());
        logEntry.setProviderId(ctx.getProviderId());
        logEntry.setProviderName(ctx.getProviderName());
        logEntry.setIsStream(ctx.getStream());
        logEntry.setInputTokens(ctx.getInputTokens());
        logEntry.setOutputTokens(ctx.getOutputTokens());
        logEntry.setCacheReadTokens(ctx.getCacheReadTokens());
        logEntry.setCacheCreationTokens(ctx.getCacheCreationTokens());
        logEntry.setFirstTokenTime(ctx.getFirstTokenTime());
        logEntry.setFirstTokenMs(ctx.getFirstTokenMs());
        logEntry.setTotalTime(ctx.getTotalTime());
        logEntry.setTotalTimeMs(ctx.getTotalTimeMs());
        logEntry.setCost(ctx.getCost());
        logEntry.setStatus(ctx.getStatus());
        logEntry.setErrorStage(ctx.getErrorStage());
        logEntry.setErrorMessage(ctx.getErrorMessage());
        logEntry.setRetryCount(ctx.getRetryCount());
        logEntry.setApiKey(ctx.getApiKey());
        logEntry.setRequestIp(ctx.getRequestIp());
        logEntry.setProtocolConversion(ctx.getProtocolConversion());

        boolean keepPayloads = includePayloads && shouldKeepPayloads(ctx);
        logEntry.setRequestContent(keepPayloads ? ctx.getRequestContent() : null);
        logEntry.setResponseContent(keepPayloads ? ctx.getResponseContent() : null);

        return logEntry;
    }

    private boolean shouldKeepPayloads(RequestLogContext ctx) {
        if (!"SUCCESS".equalsIgnoreCase(ctx.getStatus())) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < loggingProperties.getSuccessPayloadSampleRate();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            flushBatch();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            while (queue != null && !queue.isEmpty()) {
                flushBatch();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
