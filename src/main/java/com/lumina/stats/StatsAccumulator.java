package com.lumina.stats;

import com.lumina.entity.RequestLog;
import com.lumina.mapper.StatsDailyMapper;
import com.lumina.mapper.StatsHourlyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsAccumulator {

    private final StatsDailyMapper statsDailyMapper;
    private final StatsHourlyMapper statsHourlyMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_PREFIX = "lumina:stats:";
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void accumulate(Collection<RequestLog> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        Map<String, AggregateKey> hourlyAgg = new HashMap<>();
        Map<String, AggregateKey> dailyAgg = new HashMap<>();

        for (RequestLog logEntry : batch) {
            LocalDateTime logTime = resolveLogTime(logEntry);
            if (logTime == null) {
                continue;
            }

            String hourKey = logTime.format(HOUR_FMT);
            String dateKey = logTime.toLocalDate().format(DATE_FMT);
            boolean success = "SUCCESS".equalsIgnoreCase(logEntry.getStatus());
            int inputTokens = logEntry.getInputTokens() != null ? logEntry.getInputTokens() : 0;
            int outputTokens = logEntry.getOutputTokens() != null ? logEntry.getOutputTokens() : 0;
            BigDecimal cost = logEntry.getCost() != null ? logEntry.getCost() : BigDecimal.ZERO;
            int latencyMs = logEntry.getTotalTimeMs() != null ? logEntry.getTotalTimeMs() : 0;
            int cacheReadTokens = logEntry.getCacheReadTokens() != null ? logEntry.getCacheReadTokens() : 0;
            int cacheCreationTokens = logEntry.getCacheCreationTokens() != null ? logEntry.getCacheCreationTokens() : 0;

            String hourAggKey = hourKey + "|" + logEntry.getProviderId() + "|" + logEntry.getActualModelName();
            hourlyAgg.computeIfAbsent(hourAggKey, k -> new AggregateKey(
                    hourKey, logEntry.getProviderId(), logEntry.getProviderName(), logEntry.getActualModelName()
            )).add(success, inputTokens, outputTokens, cost, latencyMs, cacheReadTokens, cacheCreationTokens);

            String dailyAggKey = dateKey + "|" + logEntry.getProviderId() + "|" + logEntry.getActualModelName();
            dailyAgg.computeIfAbsent(dailyAggKey, k -> new AggregateKey(
                    dateKey, logEntry.getProviderId(), logEntry.getProviderName(), logEntry.getActualModelName()
            )).add(success, inputTokens, outputTokens, cost, latencyMs, cacheReadTokens, cacheCreationTokens);
        }

        flushToDb(hourlyAgg, dailyAgg);
        flushToRedis(batch);
    }

    private void flushToDb(Map<String, AggregateKey> hourlyAgg, Map<String, AggregateKey> dailyAgg) {
        try {
            for (AggregateKey agg : hourlyAgg.values()) {
                statsHourlyMapper.upsert(
                        agg.timeKey, agg.providerId, agg.providerName, agg.modelName,
                        agg.requests, agg.successCount, agg.inputTokens,
                        agg.outputTokens, agg.cost, agg.latencyMs,
                        agg.cacheReadTokens, agg.cacheCreationTokens, agg.cacheHitCount
                );
            }
            for (AggregateKey agg : dailyAgg.values()) {
                statsDailyMapper.upsert(
                        agg.timeKey, agg.providerId, agg.providerName, agg.modelName,
                        agg.requests, agg.successCount, agg.inputTokens,
                        agg.outputTokens, agg.cost, agg.latencyMs,
                        agg.cacheReadTokens, agg.cacheCreationTokens, agg.cacheHitCount
                );
            }
        } catch (Exception e) {
            log.error("统计聚合写入数据库失败", e);
        }
    }

    private void flushToRedis(Collection<RequestLog> batch) {
        try {
            String today = LocalDate.now().format(DATE_FMT);
            String totalKey = REDIS_PREFIX + "overview:total";
            String todayKey = REDIS_PREFIX + "overview:" + today;

            long totalRequests = 0;
            long totalSuccess = 0;
            long totalInputTokens = 0;
            long totalOutputTokens = 0;
            long totalLatencyMs = 0;
            double totalCost = 0.0;
            long totalCacheReadTokens = 0;
            long totalCacheCreationTokens = 0;
            long totalCacheHitCount = 0;

            for (RequestLog logEntry : batch) {
                totalRequests++;
                if ("SUCCESS".equalsIgnoreCase(logEntry.getStatus())) {
                    totalSuccess++;
                }
                totalInputTokens += logEntry.getInputTokens() != null ? logEntry.getInputTokens() : 0;
                totalOutputTokens += logEntry.getOutputTokens() != null ? logEntry.getOutputTokens() : 0;
                totalLatencyMs += logEntry.getTotalTimeMs() != null ? logEntry.getTotalTimeMs() : 0;
                totalCost += logEntry.getCost() != null ? logEntry.getCost().doubleValue() : 0.0;
                int cacheRead = logEntry.getCacheReadTokens() != null ? logEntry.getCacheReadTokens() : 0;
                int cacheCreation = logEntry.getCacheCreationTokens() != null ? logEntry.getCacheCreationTokens() : 0;
                totalCacheReadTokens += cacheRead;
                totalCacheCreationTokens += cacheCreation;
                if (cacheRead > 0) {
                    totalCacheHitCount++;
                }
            }

            redisTemplate.opsForHash().increment(totalKey, "requests", totalRequests);
            redisTemplate.opsForHash().increment(totalKey, "success", totalSuccess);
            redisTemplate.opsForHash().increment(totalKey, "inputTokens", totalInputTokens);
            redisTemplate.opsForHash().increment(totalKey, "outputTokens", totalOutputTokens);
            redisTemplate.opsForHash().increment(totalKey, "latencyMs", totalLatencyMs);
            redisTemplate.opsForHash().increment(totalKey, "costMicros", (long) (totalCost * 10000));
            redisTemplate.opsForHash().increment(totalKey, "cacheReadTokens", totalCacheReadTokens);
            redisTemplate.opsForHash().increment(totalKey, "cacheCreationTokens", totalCacheCreationTokens);
            redisTemplate.opsForHash().increment(totalKey, "cacheHitCount", totalCacheHitCount);

            redisTemplate.opsForHash().increment(todayKey, "requests", totalRequests);
            redisTemplate.opsForHash().increment(todayKey, "success", totalSuccess);
            redisTemplate.opsForHash().increment(todayKey, "inputTokens", totalInputTokens);
            redisTemplate.opsForHash().increment(todayKey, "outputTokens", totalOutputTokens);
            redisTemplate.opsForHash().increment(todayKey, "latencyMs", totalLatencyMs);
            redisTemplate.opsForHash().increment(todayKey, "costMicros", (long) (totalCost * 10000));
            redisTemplate.opsForHash().increment(todayKey, "cacheReadTokens", totalCacheReadTokens);
            redisTemplate.opsForHash().increment(todayKey, "cacheCreationTokens", totalCacheCreationTokens);
            redisTemplate.opsForHash().increment(todayKey, "cacheHitCount", totalCacheHitCount);
            redisTemplate.expire(todayKey, 48, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("统计聚合写入Redis失败（不影响主流程）", e);
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
        return LocalDateTime.now();
    }

    private static class AggregateKey {
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

        AggregateKey(String timeKey, Long providerId, String providerName, String modelName) {
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
