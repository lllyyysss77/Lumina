package com.lumina.service;

import com.lumina.dto.DashboardOverviewDto;
import com.lumina.dto.DashboardObservabilityDto;
import com.lumina.dto.ModelTokenUsageDto;
import com.lumina.dto.ProviderStatsDto;
import com.lumina.dto.RequestTrafficDto;
import com.lumina.dto.CircuitBreakerStatusResponse;
import com.lumina.mapper.DashboardMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CircuitBreakerManagementService circuitBreakerManagementService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取仪表盘概览统计
     * 总请求数、预估总费用、平均延迟、成功率统计全部数据
     * 增长率/变化率基于当天对比前一天的数据
     */
    public DashboardOverviewDto getOverview() {
        // 获取全部统计数据
        DashboardOverviewDto allStats = dashboardMapper.getAllOverviewStats();

        // 计算当天和前一天的日期范围
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        // 获取当天的统计数据（用于计算增长率）
        DashboardOverviewDto todayStats = dashboardMapper.getDateRangeStats(
                todayStart.format(FORMATTER),
                now.format(FORMATTER)
        );

        // 获取前一天的统计数据（用于计算增长率）
        DashboardOverviewDto yesterdayStats = dashboardMapper.getDateRangeStats(
                yesterdayStart.format(FORMATTER),
                todayStart.format(FORMATTER)
        );

        if (allStats != null) {
            // 计算增长率/变化率（当天 vs 前一天）
            if (yesterdayStats != null && yesterdayStats.getTotalRequests() > 0) {
                allStats.setRequestGrowthRate(
                        ((todayStats.getTotalRequests() - yesterdayStats.getTotalRequests()) * 100.0) / yesterdayStats.getTotalRequests()
                );
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
                        ((todayStats.getTotalCost().doubleValue() - yesterdayStats.getTotalCost().doubleValue()) * 100.0) / yesterdayStats.getTotalCost().doubleValue()
                );
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

            if (allStats.getTotalTokens() == null) {
                allStats.setTotalTokens(0L);
            }
            if (allStats.getTotalCost() == null) {
                allStats.setTotalCost(BigDecimal.ZERO);
            }
        }

        return allStats;
    }

    /**
     * 获取24小时请求流量
     * 返回完整的 0-23 小时数据，没有数据的小时显示为 0
     */
    public List<RequestTrafficDto> getRequestTraffic() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);

        // 从数据库获取有数据的小时
        List<RequestTrafficDto> dbResults = dashboardMapper.getRequestTraffic(last24Hours.format(FORMATTER));

        // 创建一个 Map 用于快速查找，key 为小时数
        java.util.Map<Integer, RequestTrafficDto> dataMap = new java.util.HashMap<>();
        for (RequestTrafficDto dto : dbResults) {
            dataMap.put(dto.getHour(), dto);
        }

        // 构建完整的 0-23 小时数据
        List<RequestTrafficDto> result = new java.util.ArrayList<>();
        LocalDateTime currentHour = last24Hours.withMinute(0).withSecond(0).withNano(0);

        for (int i = 0; i < 24; i++) {
            int hour = currentHour.getHour();
            long timestamp = currentHour.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

            RequestTrafficDto dto = dataMap.get(hour);
            if (dto != null) {
                // 有数据，使用数据库返回的数据
                result.add(dto);
            } else {
                // 没有数据，创建一个 requestCount 为 0 的数据点
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
     */
    public List<ModelTokenUsageDto> getModelTokenUsage() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(48);
        List<ModelTokenUsageDto> usageList = dashboardMapper.getModelTokenUsage(last24Hours.format(FORMATTER));

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
     */
    public List<ProviderStatsDto> getProviderStats(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }

        List<ProviderStatsDto> statsList = dashboardMapper.getProviderStats(limit);

        for (int i = 0; i < statsList.size(); i++) {
            ProviderStatsDto stats = statsList.get(i);
            stats.setRank(i + 1);
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
