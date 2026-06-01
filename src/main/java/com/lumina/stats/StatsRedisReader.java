package com.lumina.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsRedisReader {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_PREFIX = "lumina:stats:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public StatsSnapshot getGlobalTotal() {
        return readHash(REDIS_PREFIX + "overview:total");
    }

    public StatsSnapshot getTodayStats() {
        String today = LocalDate.now().format(DATE_FMT);
        return readHash(REDIS_PREFIX + "overview:" + today);
    }

    public StatsSnapshot getYesterdayStats() {
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);
        return readHash(REDIS_PREFIX + "overview:" + yesterday);
    }

    public boolean hasData() {
        String key = REDIS_PREFIX + "overview:total";
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void resetAll() {
        String totalKey = REDIS_PREFIX + "overview:total";
        String todayKey = REDIS_PREFIX + "overview:" + LocalDate.now().format(DATE_FMT);
        String yesterdayKey = REDIS_PREFIX + "overview:" + LocalDate.now().minusDays(1).format(DATE_FMT);
        redisTemplate.delete(totalKey);
        redisTemplate.delete(todayKey);
        redisTemplate.delete(yesterdayKey);
    }

    private StatsSnapshot readHash(String key) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return StatsSnapshot.EMPTY;
        }
        return new StatsSnapshot(
                parseLong(entries, "requests"),
                parseLong(entries, "success"),
                parseLong(entries, "inputTokens"),
                parseLong(entries, "outputTokens"),
                parseLong(entries, "latencyMs"),
                parseLong(entries, "costMicros") / 10000.0,
                parseLong(entries, "cacheReadTokens"),
                parseLong(entries, "cacheCreationTokens"),
                parseLong(entries, "cacheHitCount")
        );
    }

    private long parseLong(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) return 0L;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public record StatsSnapshot(
            long requests,
            long success,
            long inputTokens,
            long outputTokens,
            long latencyMs,
            double cost,
            long cacheReadTokens,
            long cacheCreationTokens,
            long cacheHitCount
    ) {
        public static final StatsSnapshot EMPTY = new StatsSnapshot(0, 0, 0, 0, 0, 0.0, 0, 0, 0);

        public double avgLatency() {
            return requests > 0 ? (double) latencyMs / requests : 0.0;
        }

        public double successRate() {
            return requests > 0 ? success * 100.0 / requests : 0.0;
        }

        public double cacheHitRate() {
            return requests > 0 ? cacheHitCount * 100.0 / requests : 0.0;
        }
    }
}
