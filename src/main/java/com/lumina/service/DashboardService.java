package com.lumina.service;

import com.lumina.dto.DashboardOverviewDto;
import com.lumina.dto.DashboardObservabilityDto;
import com.lumina.dto.HealthHeatmapDto;
import com.lumina.dto.ModelTokenUsageDto;
import com.lumina.dto.ProviderStatsDto;
import com.lumina.dto.RequestTrafficDto;
import com.lumina.dto.CircuitBreakerStatusResponse;
import com.lumina.entity.StatsDaily;
import com.lumina.entity.StatsHourly;
import com.lumina.mapper.DashboardMapper;
import com.lumina.mapper.StatsDailyMapper;
import com.lumina.mapper.StatsHourlyMapper;
import com.lumina.stats.StatsRedisReader;
import com.lumina.stats.StatsRedisReader.StatsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private StatsDailyMapper statsDailyMapper;

    @Autowired
    private StatsHourlyMapper statsHourlyMapper;

    @Autowired
    private StatsRedisReader statsRedisReader;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CircuitBreakerManagementService circuitBreakerManagementService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取仪表盘概览统计
     * 优先从 Redis 读取实时计数器，回退到聚合表
     */
    public DashboardOverviewDto getOverview() {
        if (statsRedisReader.hasData()) {
            return getOverviewFromRedis();
        }
        return getOverviewFromAggTable();
    }

    private DashboardOverviewDto getOverviewFromRedis() {
        StatsSnapshot total = statsRedisReader.getGlobalTotal();
        StatsSnapshot today = statsRedisReader.getTodayStats();
        StatsSnapshot yesterday = statsRedisReader.getYesterdayStats();

        DashboardOverviewDto dto = DashboardOverviewDto.builder()
                .totalRequests(total.requests())
                .totalTokens(total.inputTokens() + total.outputTokens())
                .totalCost(BigDecimal.valueOf(total.cost()))
                .avgLatency(total.avgLatency())
                .successRate(total.successRate())
                .cacheHitCount(total.cacheHitCount())
                .cacheHitRate(total.cacheHitRate())
                .cacheReadTokens(total.cacheReadTokens())
                .build();

        computeGrowthRates(dto, today, yesterday);
        return dto;
    }

    private DashboardOverviewDto getOverviewFromAggTable() {
        String today = LocalDate.now().format(DATE_FMT);
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);

        StatsDaily globalTotal = statsDailyMapper.selectGlobalTotal();
        StatsDaily todayStats = statsDailyMapper.selectGlobalByDate(today);
        StatsDaily yesterdayStats = statsDailyMapper.selectGlobalByDate(yesterday);

        if (globalTotal == null || globalTotal.getTotalRequests() == null || globalTotal.getTotalRequests() == 0) {
            return getOverviewFallback();
        }

        long totalReqs = globalTotal.getTotalRequests();
        long cacheHitCount = globalTotal.getCacheHitCount() != null ? globalTotal.getCacheHitCount() : 0;
        long cacheReadTokens = globalTotal.getTotalCacheReadTokens() != null ? globalTotal.getTotalCacheReadTokens() : 0;
        DashboardOverviewDto dto = DashboardOverviewDto.builder()
                .totalRequests(totalReqs)
                .totalTokens(globalTotal.getTotalInputTokens() + globalTotal.getTotalOutputTokens())
                .totalCost(globalTotal.getTotalCost())
                .avgLatency(totalReqs > 0 ? (double) globalTotal.getTotalLatencyMs() / totalReqs : 0.0)
                .successRate(totalReqs > 0 ? globalTotal.getSuccessCount() * 100.0 / totalReqs : 0.0)
                .cacheHitCount(cacheHitCount)
                .cacheHitRate(totalReqs > 0 ? cacheHitCount * 100.0 / totalReqs : 0.0)
                .cacheReadTokens(cacheReadTokens)
                .build();

        StatsSnapshot todaySnap = toSnapshot(todayStats);
        StatsSnapshot yesterdaySnap = toSnapshot(yesterdayStats);
        computeGrowthRates(dto, todaySnap, yesterdaySnap);
        return dto;
    }

    /**
     * 聚合表无数据时回退到原始查询（仅在首次部署、rebuild 前使用）
     */
    private DashboardOverviewDto getOverviewFallback() {
        DashboardOverviewDto allStats = dashboardMapper.getAllOverviewStats();
        if (allStats == null) {
            return DashboardOverviewDto.builder()
                    .totalRequests(0L).totalTokens(0L).totalCost(BigDecimal.ZERO)
                    .avgLatency(0.0).successRate(0.0)
                    .requestGrowthRate(0.0).tokenGrowthRate(0.0).costGrowthRate(0.0)
                    .latencyChange(0.0).successRateChange(0.0)
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        DashboardOverviewDto todayStats = dashboardMapper.getDateRangeStats(
                todayStart.format(FORMATTER), now.format(FORMATTER));
        DashboardOverviewDto yesterdayStats = dashboardMapper.getDateRangeStats(
                yesterdayStart.format(FORMATTER), todayStart.format(FORMATTER));

        if (yesterdayStats != null && yesterdayStats.getTotalRequests() > 0) {
            allStats.setRequestGrowthRate(
                    ((todayStats.getTotalRequests() - yesterdayStats.getTotalRequests()) * 100.0) / yesterdayStats.getTotalRequests());
        } else {
            allStats.setRequestGrowthRate(todayStats != null && todayStats.getTotalRequests() > 0 ? 100.0 : 0.0);
        }

        long todayTokens = todayStats != null && todayStats.getTotalTokens() != null ? todayStats.getTotalTokens() : 0L;
        long yesterdayTokens = yesterdayStats != null && yesterdayStats.getTotalTokens() != null ? yesterdayStats.getTotalTokens() : 0L;
        if (yesterdayTokens > 0) {
            allStats.setTokenGrowthRate(((todayTokens - yesterdayTokens) * 100.0) / yesterdayTokens);
        } else {
            allStats.setTokenGrowthRate(todayTokens > 0 ? 100.0 : 0.0);
        }

        if (yesterdayStats != null && yesterdayStats.getTotalCost().doubleValue() > 0) {
            allStats.setCostGrowthRate(
                    ((todayStats.getTotalCost().doubleValue() - yesterdayStats.getTotalCost().doubleValue()) * 100.0) / yesterdayStats.getTotalCost().doubleValue());
        } else {
            allStats.setCostGrowthRate(todayStats != null && todayStats.getTotalCost().doubleValue() > 0 ? 100.0 : 0.0);
        }

        if (yesterdayStats != null) {
            allStats.setLatencyChange(todayStats.getAvgLatency() - yesterdayStats.getAvgLatency());
            allStats.setSuccessRateChange(todayStats.getSuccessRate() - yesterdayStats.getSuccessRate());
        } else {
            allStats.setLatencyChange(0.0);
            allStats.setSuccessRateChange(0.0);
        }

        if (allStats.getTotalTokens() == null) allStats.setTotalTokens(0L);
        if (allStats.getTotalCost() == null) allStats.setTotalCost(BigDecimal.ZERO);

        return allStats;
    }

    private void computeGrowthRates(DashboardOverviewDto dto, StatsSnapshot today, StatsSnapshot yesterday) {
        if (yesterday.requests() > 0) {
            dto.setRequestGrowthRate(((today.requests() - yesterday.requests()) * 100.0) / yesterday.requests());
        } else {
            dto.setRequestGrowthRate(today.requests() > 0 ? 100.0 : 0.0);
        }

        long todayTokens = today.inputTokens() + today.outputTokens();
        long yesterdayTokens = yesterday.inputTokens() + yesterday.outputTokens();
        if (yesterdayTokens > 0) {
            dto.setTokenGrowthRate(((todayTokens - yesterdayTokens) * 100.0) / yesterdayTokens);
        } else {
            dto.setTokenGrowthRate(todayTokens > 0 ? 100.0 : 0.0);
        }

        if (yesterday.cost() > 0) {
            dto.setCostGrowthRate(((today.cost() - yesterday.cost()) * 100.0) / yesterday.cost());
        } else {
            dto.setCostGrowthRate(today.cost() > 0 ? 100.0 : 0.0);
        }

        dto.setLatencyChange(today.avgLatency() - yesterday.avgLatency());
        dto.setSuccessRateChange(today.successRate() - yesterday.successRate());
    }

    private StatsSnapshot toSnapshot(StatsDaily stats) {
        if (stats == null || stats.getTotalRequests() == null || stats.getTotalRequests() == 0) {
            return StatsSnapshot.EMPTY;
        }
        return new StatsSnapshot(
                stats.getTotalRequests(),
                stats.getSuccessCount() != null ? stats.getSuccessCount() : 0,
                stats.getTotalInputTokens() != null ? stats.getTotalInputTokens() : 0,
                stats.getTotalOutputTokens() != null ? stats.getTotalOutputTokens() : 0,
                stats.getTotalLatencyMs() != null ? stats.getTotalLatencyMs() : 0,
                stats.getTotalCost() != null ? stats.getTotalCost().doubleValue() : 0.0,
                stats.getTotalCacheReadTokens() != null ? stats.getTotalCacheReadTokens() : 0,
                stats.getTotalCacheCreationTokens() != null ? stats.getTotalCacheCreationTokens() : 0,
                stats.getCacheHitCount() != null ? stats.getCacheHitCount() : 0
        );
    }

    /**
     * 获取24小时请求流量
     * 从 stats_hourly 聚合表读取，回退到原始查询
     */
    public List<RequestTrafficDto> getRequestTraffic() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);

        String startHour = last24Hours.format(HOUR_FMT);
        String endHour = now.format(HOUR_FMT);

        List<StatsHourly> hourlyData = statsHourlyMapper.selectByHourRange(startHour, endHour);

        if (hourlyData == null || hourlyData.isEmpty()) {
            return getRequestTrafficFallback();
        }

        java.util.Map<Integer, Long> dataMap = new java.util.HashMap<>();
        for (StatsHourly h : hourlyData) {
            if (h.getStatHour() != null) {
                int hour = h.getStatHour().getHour();
                dataMap.merge(hour, h.getTotalRequests() != null ? h.getTotalRequests() : 0L, Long::sum);
            }
        }

        List<RequestTrafficDto> result = new java.util.ArrayList<>();
        LocalDateTime currentHour = last24Hours.withMinute(0).withSecond(0).withNano(0);

        for (int i = 0; i < 24; i++) {
            int hour = currentHour.getHour();
            long timestamp = currentHour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long count = dataMap.getOrDefault(hour, 0L);

            result.add(RequestTrafficDto.builder()
                    .hour(hour)
                    .requestCount(count)
                    .timestamp(timestamp)
                    .build());

            currentHour = currentHour.plusHours(1);
        }

        return result;
    }

    private List<RequestTrafficDto> getRequestTrafficFallback() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);

        List<RequestTrafficDto> dbResults = dashboardMapper.getRequestTraffic(last24Hours.format(FORMATTER));

        java.util.Map<Integer, RequestTrafficDto> dataMap = new java.util.HashMap<>();
        for (RequestTrafficDto dto : dbResults) {
            dataMap.put(dto.getHour(), dto);
        }

        List<RequestTrafficDto> result = new java.util.ArrayList<>();
        LocalDateTime currentHour = last24Hours.withMinute(0).withSecond(0).withNano(0);

        for (int i = 0; i < 24; i++) {
            int hour = currentHour.getHour();
            long timestamp = currentHour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            RequestTrafficDto dto = dataMap.get(hour);
            if (dto != null) {
                result.add(dto);
            } else {
                result.add(RequestTrafficDto.builder()
                        .hour(hour)
                        .requestCount(0L)
                        .timestamp(timestamp)
                        .build());
            }

            currentHour = currentHour.plusHours(1);
        }

        return result;
    }

    /**
     * 获取模型 Token 使用统计
     * 从 stats_hourly 聚合表按模型汇总
     */
    public List<ModelTokenUsageDto> getModelTokenUsage() {
        LocalDateTime last48Hours = LocalDateTime.now().minusHours(48);
        String startHour = last48Hours.format(HOUR_FMT);
        String endHour = LocalDateTime.now().format(HOUR_FMT);

        List<StatsHourly> modelData = statsHourlyMapper.selectModelUsageByHourRange(startHour, endHour, 10);

        if (modelData == null || modelData.isEmpty()) {
            return getModelTokenUsageFallback();
        }

        List<ModelTokenUsageDto> usageList = new java.util.ArrayList<>();
        long totalTokens = 0;

        for (StatsHourly h : modelData) {
            long inTokens = h.getTotalInputTokens() != null ? h.getTotalInputTokens() : 0;
            long outTokens = h.getTotalOutputTokens() != null ? h.getTotalOutputTokens() : 0;
            long total = inTokens + outTokens;
            long reqs = h.getTotalRequests() != null ? h.getTotalRequests() : 0;
            long cacheHits = h.getCacheHitCount() != null ? h.getCacheHitCount() : 0;
            long cacheRead = h.getTotalCacheReadTokens() != null ? h.getTotalCacheReadTokens() : 0;
            totalTokens += total;

            usageList.add(ModelTokenUsageDto.builder()
                    .modelName(h.getModelName())
                    .inputTokens(inTokens)
                    .outputTokens(outTokens)
                    .totalTokens(total)
                    .requestCount(reqs)
                    .cacheReadTokens(cacheRead)
                    .cacheHitRate(reqs > 0 ? cacheHits * 100.0 / reqs : 0.0)
                    .build());
        }

        final long finalTotal = totalTokens;
        usageList.forEach(usage -> {
            if (finalTotal > 0) {
                usage.setPercentage((usage.getTotalTokens() * 100.0) / finalTotal);
            } else {
                usage.setPercentage(0.0);
            }
        });

        return usageList;
    }

    private List<ModelTokenUsageDto> getModelTokenUsageFallback() {
        LocalDateTime last48Hours = LocalDateTime.now().minusHours(48);
        List<ModelTokenUsageDto> usageList = dashboardMapper.getModelTokenUsage(last48Hours.format(FORMATTER));

        long totalTokens = usageList.stream()
                .mapToLong(ModelTokenUsageDto::getTotalTokens)
                .sum();

        usageList.forEach(usage -> {
            if (totalTokens > 0) {
                usage.setPercentage((usage.getTotalTokens() * 100.0) / totalTokens);
            } else {
                usage.setPercentage(0.0);
            }
        });

        return usageList;
    }

    /**
     * 获取供应商统计排名
     * 从 stats_daily 聚合表按供应商汇总
     */
    public List<ProviderStatsDto> getProviderStats(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }

        List<StatsDaily> providerData = statsDailyMapper.selectProviderStats(limit);

        if (providerData == null || providerData.isEmpty()) {
            return getProviderStatsFallback(limit);
        }

        List<ProviderStatsDto> statsList = new java.util.ArrayList<>();
        for (int i = 0; i < providerData.size(); i++) {
            StatsDaily d = providerData.get(i);
            long totalReqs = d.getTotalRequests() != null ? d.getTotalRequests() : 0;
            long successCnt = d.getSuccessCount() != null ? d.getSuccessCount() : 0;
            long latencyMs = d.getTotalLatencyMs() != null ? d.getTotalLatencyMs() : 0;

            statsList.add(ProviderStatsDto.builder()
                    .rank(i + 1)
                    .providerId(d.getProviderId())
                    .providerName(d.getProviderName())
                    .callCount(totalReqs)
                    .estimatedCost(d.getTotalCost() != null ? d.getTotalCost() : BigDecimal.ZERO)
                    .avgLatency(totalReqs > 0 ? (double) latencyMs / totalReqs : 0.0)
                    .successRate(totalReqs > 0 ? successCnt * 100.0 / totalReqs : 0.0)
                    .build());
        }

        return statsList;
    }

    private List<ProviderStatsDto> getProviderStatsFallback(int limit) {
        List<ProviderStatsDto> statsList = dashboardMapper.getProviderStats(limit);
        for (int i = 0; i < statsList.size(); i++) {
            statsList.get(i).setRank(i + 1);
        }
        return statsList;
    }

    public DashboardObservabilityDto getObservability() {
        List<DashboardObservabilityDto.CacheMetric> caches = List.of(
                buildCacheMetric("group_config"),
                buildCacheMetric("api_key"),
                buildCacheMetric("model_price")
        );

        long totalCacheHits = caches.stream().mapToLong(DashboardObservabilityDto.CacheMetric::getHits).sum();
        long totalCacheMisses = caches.stream().mapToLong(DashboardObservabilityDto.CacheMetric::getMisses).sum();
        long totalCacheExpired = caches.stream().mapToLong(DashboardObservabilityDto.CacheMetric::getExpired).sum();
        long totalCacheLookups = totalCacheHits + totalCacheMisses + totalCacheExpired;

        long saprSelections = counterCount("lumina_provider_selection_total", "strategy", "sapr");
        long roundRobinSelections = counterCount("lumina_provider_selection_total", "strategy", "round_robin");
        long fallbackToRoundRobin = counterCount("lumina_provider_fallback_total", "strategy", "round_robin");
        long skippedExcluded = counterCount("lumina_provider_skipped_total", "reason", "excluded");
        long skippedCircuitOpen = counterCount("lumina_provider_skipped_total", "reason", "circuit_open");
        long skippedCircuitHalfOpen = counterCount("lumina_provider_skipped_total", "reason", "circuit_half_open");
        long bulkheadRejectedNonStream = counterCount("lumina_bulkhead_rejections_total", "stream", "false");
        long bulkheadRejectedStream = counterCount("lumina_bulkhead_rejections_total", "stream", "true");
        long failoverAttemptsNonStream = counterCount("lumina_failover_attempts_total", "stream", "false");
        long failoverAttemptsStream = counterCount("lumina_failover_attempts_total", "stream", "true");
        long failoverSwitches = counterCount("lumina_failover_switch_total");
        long failoverTerminated = counterCount("lumina_failover_terminated_total");
        long providersTracked = gaugeValue("lumina_providers_registered");
        long openCircuits = gaugeValue("lumina_circuit_state_count", "state", "open");
        long halfOpenCircuits = gaugeValue("lumina_circuit_state_count", "state", "half_open");
        long logQueueSize = gaugeValue("lumina_log_queue_size");
        long logDroppedTotal = gaugeValue("lumina_log_dropped_total");
        double logBatchAvg = summaryMean("lumina_log_batch_size");
        double logFlushAvgMs = timerMeanMs("lumina_log_flush_duration");
        double failoverDepthAvg = summaryMean("lumina_failover_depth");

        List<CircuitBreakerStatusResponse> providers = circuitBreakerManagementService.listAllStatus().stream()
                .sorted(Comparator
                        .comparing((CircuitBreakerStatusResponse status) -> status.getCircuitState().name())
                        .thenComparing(CircuitBreakerStatusResponse::getScore, Comparator.reverseOrder()))
                .toList();

        return DashboardObservabilityDto.builder()
                .overview(DashboardObservabilityDto.Overview.builder()
                        .providersTracked(providersTracked)
                        .openCircuits(openCircuits)
                        .halfOpenCircuits(halfOpenCircuits)
                        .bulkheadRejections(bulkheadRejectedNonStream + bulkheadRejectedStream)
                        .logDroppedTotal(logDroppedTotal)
                        .logQueueSize(logQueueSize)
                        .cacheHitRate(ratio(totalCacheHits, totalCacheLookups))
                        .failoverSwitches(failoverSwitches)
                        .failoverTerminations(failoverTerminated)
                        .failoverDepthAvg(failoverDepthAvg)
                        .build())
                .selection(DashboardObservabilityDto.Selection.builder()
                        .saprSelections(saprSelections)
                        .roundRobinSelections(roundRobinSelections)
                        .fallbackToRoundRobin(fallbackToRoundRobin)
                        .skippedExcluded(skippedExcluded)
                        .skippedCircuitOpen(skippedCircuitOpen)
                        .skippedCircuitHalfOpen(skippedCircuitHalfOpen)
                        .bulkheadRejectedNonStream(bulkheadRejectedNonStream)
                        .bulkheadRejectedStream(bulkheadRejectedStream)
                        .failoverAttemptsNonStream(failoverAttemptsNonStream)
                        .failoverAttemptsStream(failoverAttemptsStream)
                        .build())
                .logPipeline(DashboardObservabilityDto.LogPipeline.builder()
                        .queueSize(logQueueSize)
                        .droppedTotal(logDroppedTotal)
                        .avgBatchSize(logBatchAvg)
                        .avgFlushMs(logFlushAvgMs)
                        .build())
                .caches(caches)
                .providers(providers)
                .build();
    }

    public HealthHeatmapDto getHealthHeatmap(int days) {
        if (days <= 0 || days > 30) {
            days = 7;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(days).withMinute(0).withSecond(0).withNano(0);

        String startHour = start.format(HOUR_FMT);
        String endHour = now.format(HOUR_FMT);

        List<StatsHourly> hourlyData = statsHourlyMapper.selectAggregatedByHourRange(startHour, endHour);

        java.util.Map<String, StatsHourly> dataMap = new java.util.HashMap<>();
        if (hourlyData != null) {
            for (StatsHourly h : hourlyData) {
                if (h.getStatHour() != null) {
                    dataMap.put(h.getStatHour().format(HOUR_FMT), h);
                }
            }
        }

        List<HealthHeatmapDto.HeatmapCell> cells = new java.util.ArrayList<>();
        long totalRequests = 0;
        long totalSuccess = 0;

        LocalDateTime cursor = start;
        while (!cursor.isAfter(now)) {
            String hourKey = cursor.withMinute(0).format(HOUR_FMT);
            long timestamp = cursor.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            StatsHourly stats = dataMap.get(hourKey);
            long hourReqs = stats != null && stats.getTotalRequests() != null ? stats.getTotalRequests() : 0;
            long hourSuccess = stats != null && stats.getSuccessCount() != null ? stats.getSuccessCount() : 0;
            long reqs = hourReqs / 4;
            long success = hourSuccess / 4;

            cells.add(HealthHeatmapDto.HeatmapCell.builder()
                    .timestamp(timestamp)
                    .totalRequests(reqs)
                    .successRequests(success)
                    .successRate(reqs > 0 ? success * 100.0 / reqs : -1)
                    .build());

            totalRequests += reqs;
            totalSuccess += success;
            cursor = cursor.plusMinutes(15);
        }

        return HealthHeatmapDto.builder()
                .overallSuccessRate(totalRequests > 0 ? totalSuccess * 100.0 / totalRequests : 0)
                .days(days)
                .cells(cells)
                .build();
    }

    private DashboardObservabilityDto.CacheMetric buildCacheMetric(String cacheName) {
        long hits = counterCount("lumina_cache_lookups_total", "cache", cacheName, "result", "hit");
        long misses = counterCount("lumina_cache_lookups_total", "cache", cacheName, "result", "miss");
        long expired = counterCount("lumina_cache_lookups_total", "cache", cacheName, "result", "expired");
        long loads = counterCount("lumina_cache_loads_total", "cache", cacheName, "result", "loaded");
        long totalLookups = hits + misses + expired;
        return DashboardObservabilityDto.CacheMetric.builder()
                .cache(cacheName)
                .hits(hits)
                .misses(misses)
                .expired(expired)
                .loads(loads)
                .hitRate(ratio(hits, totalLookups))
                .avgLoadMs(timerMeanMs("lumina_cache_load_duration", "cache", cacheName))
                .build();
    }

    private long counterCount(String name, String... tags) {
        return Math.round(meterRegistry.find(name).tags(tags).counters().stream()
                .mapToDouble(Counter::count)
                .sum());
    }

    private long gaugeValue(String name, String... tags) {
        return Math.round(meterRegistry.find(name).tags(tags).gauges().stream()
                .mapToDouble(Gauge::value)
                .sum());
    }

    private double summaryMean(String name, String... tags) {
        return meterRegistry.find(name).tags(tags).summaries().stream()
                .mapToDouble(DistributionSummary::mean)
                .findFirst()
                .orElse(0.0d);
    }

    private double timerMeanMs(String name, String... tags) {
        return meterRegistry.find(name).tags(tags).timers().stream()
                .mapToDouble(timer -> timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
                .findFirst()
                .orElse(0.0d);
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return numerator * 100.0d / denominator;
    }
}
