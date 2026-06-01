package com.lumina.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.RequestLog;
import com.lumina.entity.StatsDaily;
import com.lumina.mapper.RequestLogMapper;
import com.lumina.mapper.StatsDailyMapper;
import com.lumina.mapper.StatsHourlyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsRebuildJob {

    private final RequestLogMapper requestLogMapper;
    private final StatsDailyMapper statsDailyMapper;
    private final StatsHourlyMapper statsHourlyMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int BATCH_SIZE = 5000;
    private static final String REDIS_PREFIX = "lumina:stats:";

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    @Async
    public void rebuildAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("统计重建任务已在运行中，跳过");
            return;
        }

        try {
            log.info("开始重建统计聚合数据...");
            long startTime = System.currentTimeMillis();

            statsDailyMapper.deleteAll();
            statsHourlyMapper.deleteAll();

            long totalProcessed = 0;
            long currentPage = 1;

            while (true) {
                Page<RequestLog> page = new Page<>(currentPage, BATCH_SIZE);
                page.setOptimizeCountSql(false);
                page.setSearchCount(false);

                LambdaQueryWrapper<RequestLog> wrapper = new LambdaQueryWrapper<RequestLog>()
                        .select(
                                RequestLog::getId,
                                RequestLog::getRequestTime,
                                RequestLog::getProviderId,
                                RequestLog::getProviderName,
                                RequestLog::getActualModelName,
                                RequestLog::getInputTokens,
                                RequestLog::getOutputTokens,
                                RequestLog::getTotalTimeMs,
                                RequestLog::getCost,
                                RequestLog::getStatus,
                                RequestLog::getCreatedAt
                        )
                        .orderByAsc(RequestLog::getId);

                Page<RequestLog> result = requestLogMapper.selectPage(page, wrapper);
                List<RequestLog> records = result.getRecords();

                if (records.isEmpty()) {
                    break;
                }

                processBatch(records);
                totalProcessed += records.size();

                if (totalProcessed % 50000 == 0) {
                    log.info("统计重建进度: 已处理 {} 条记录", totalProcessed);
                }

                if (records.size() < BATCH_SIZE) {
                    break;
                }
                currentPage++;
            }

            rebuildRedisCounters();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("统计重建完成，共处理 {} 条记录，耗时 {}ms", totalProcessed, elapsed);
        } catch (Exception e) {
            log.error("统计重建任务失败", e);
        } finally {
            running.set(false);
        }
    }

    private void processBatch(List<RequestLog> batch) {
        Map<String, AggBucket> hourlyAgg = new HashMap<>();
        Map<String, AggBucket> dailyAgg = new HashMap<>();

        for (RequestLog logEntry : batch) {
            LocalDateTime logTime = resolveLogTime(logEntry);
            if (logTime == null) continue;

            String hourKey = logTime.format(HOUR_FMT);
            String dateKey = logTime.toLocalDate().format(DATE_FMT);
            boolean success = "SUCCESS".equalsIgnoreCase(logEntry.getStatus());
            int inputTokens = logEntry.getInputTokens() != null ? logEntry.getInputTokens() : 0;
            int outputTokens = logEntry.getOutputTokens() != null ? logEntry.getOutputTokens() : 0;
            BigDecimal cost = logEntry.getCost() != null ? logEntry.getCost() : BigDecimal.ZERO;
            int latencyMs = logEntry.getTotalTimeMs() != null ? logEntry.getTotalTimeMs() : 0;
            int cacheReadTokens = logEntry.getCacheReadTokens() != null ? logEntry.getCacheReadTokens() : 0;
            int cacheCreationTokens = logEntry.getCacheCreationTokens() != null ? logEntry.getCacheCreationTokens() : 0;

            String hKey = hourKey + "|" + logEntry.getProviderId() + "|" + logEntry.getActualModelName();
            hourlyAgg.computeIfAbsent(hKey, k -> new AggBucket(
                    hourKey, logEntry.getProviderId(), logEntry.getProviderName(), logEntry.getActualModelName()
            )).add(success, inputTokens, outputTokens, cost, latencyMs, cacheReadTokens, cacheCreationTokens);

            String dKey = dateKey + "|" + logEntry.getProviderId() + "|" + logEntry.getActualModelName();
            dailyAgg.computeIfAbsent(dKey, k -> new AggBucket(
                    dateKey, logEntry.getProviderId(), logEntry.getProviderName(), logEntry.getActualModelName()
            )).add(success, inputTokens, outputTokens, cost, latencyMs, cacheReadTokens, cacheCreationTokens);
        }

        for (AggBucket agg : hourlyAgg.values()) {
            statsHourlyMapper.upsert(agg.timeKey, agg.providerId, agg.providerName, agg.modelName,
                    agg.requests, agg.successCount, agg.inputTokens, agg.outputTokens, agg.cost, agg.latencyMs,
                    agg.cacheReadTokens, agg.cacheCreationTokens, agg.cacheHitCount);
        }
        for (AggBucket agg : dailyAgg.values()) {
            statsDailyMapper.upsert(agg.timeKey, agg.providerId, agg.providerName, agg.modelName,
                    agg.requests, agg.successCount, agg.inputTokens, agg.outputTokens, agg.cost, agg.latencyMs,
                    agg.cacheReadTokens, agg.cacheCreationTokens, agg.cacheHitCount);
        }
    }

    private void rebuildRedisCounters() {
        log.info("重建 Redis 统计计数器...");

        String totalKey = REDIS_PREFIX + "overview:total";
        String todayKey = REDIS_PREFIX + "overview:" + LocalDate.now().format(DATE_FMT);
        String yesterdayKey = REDIS_PREFIX + "overview:" + LocalDate.now().minusDays(1).format(DATE_FMT);

        redisTemplate.delete(totalKey);
        redisTemplate.delete(todayKey);
        redisTemplate.delete(yesterdayKey);

        StatsDaily globalTotal = statsDailyMapper.selectGlobalTotal();
        if (globalTotal != null && globalTotal.getTotalRequests() != null) {
            setRedisHash(totalKey, globalTotal, -1);
        }

        StatsDaily todayStats = statsDailyMapper.selectGlobalByDate(LocalDate.now().format(DATE_FMT));
        if (todayStats != null && todayStats.getTotalRequests() != null) {
            setRedisHash(todayKey, todayStats, 48);
        }

        StatsDaily yesterdayStats = statsDailyMapper.selectGlobalByDate(LocalDate.now().minusDays(1).format(DATE_FMT));
        if (yesterdayStats != null && yesterdayStats.getTotalRequests() != null) {
            setRedisHash(yesterdayKey, yesterdayStats, 48);
        }
    }

    private void setRedisHash(String key, StatsDaily stats, int expireHours) {
        Map<String, String> fields = new HashMap<>();
        fields.put("requests", String.valueOf(stats.getTotalRequests()));
        fields.put("success", String.valueOf(stats.getSuccessCount() != null ? stats.getSuccessCount() : 0));
        fields.put("inputTokens", String.valueOf(stats.getTotalInputTokens() != null ? stats.getTotalInputTokens() : 0));
        fields.put("outputTokens", String.valueOf(stats.getTotalOutputTokens() != null ? stats.getTotalOutputTokens() : 0));
        fields.put("latencyMs", String.valueOf(stats.getTotalLatencyMs() != null ? stats.getTotalLatencyMs() : 0));
        long costMicros = stats.getTotalCost() != null ? stats.getTotalCost().multiply(BigDecimal.valueOf(10000)).longValue() : 0;
        fields.put("costMicros", String.valueOf(costMicros));
        fields.put("cacheReadTokens", String.valueOf(stats.getTotalCacheReadTokens() != null ? stats.getTotalCacheReadTokens() : 0));
        fields.put("cacheCreationTokens", String.valueOf(stats.getTotalCacheCreationTokens() != null ? stats.getTotalCacheCreationTokens() : 0));
        fields.put("cacheHitCount", String.valueOf(stats.getCacheHitCount() != null ? stats.getCacheHitCount() : 0));

        redisTemplate.opsForHash().putAll(key, fields);
        if (expireHours > 0) {
            redisTemplate.expire(key, expireHours, TimeUnit.HOURS);
        }
    }

    private LocalDateTime resolveLogTime(RequestLog logEntry) {
        if (logEntry.getCreatedAt() != null) {
            return logEntry.getCreatedAt();
        }
        if (logEntry.getRequestTime() != null) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(logEntry.getRequestTime()), ZoneId.systemDefault());
        }
        return null;
    }

    private static class AggBucket {
        final String timeKey;
        final Long providerId;
        final String providerName;
        final String modelName;
        long requests;
        long successCount;
        long inputTokens;
        long outputTokens;
        BigDecimal cost = BigDecimal.ZERO;
        long latencyMs;
        long cacheReadTokens;
        long cacheCreationTokens;
        long cacheHitCount;

        AggBucket(String timeKey, Long providerId, String providerName, String modelName) {
            this.timeKey = timeKey;
            this.providerId = providerId;
            this.providerName = providerName;
            this.modelName = modelName;
        }

        void add(boolean success, int inTokens, int outTokens, BigDecimal c, int latency,
                 int cacheRead, int cacheCreation) {
            requests++;
            if (success) successCount++;
            inputTokens += inTokens;
            outputTokens += outTokens;
            cost = cost.add(c);
            latencyMs += latency;
            cacheReadTokens += cacheRead;
            cacheCreationTokens += cacheCreation;
            if (cacheRead > 0) cacheHitCount++;
        }
    }
}
