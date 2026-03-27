package com.lumina.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardObservabilityDto {

    private Overview overview;
    private Selection selection;
    private LogPipeline logPipeline;
    private List<CacheMetric> caches;
    private List<CircuitBreakerStatusResponse> providers;

    @Data
    @Builder
    public static class Overview {
        private long providersTracked;
        private long openCircuits;
        private long halfOpenCircuits;
        private long bulkheadRejections;
        private long logDroppedTotal;
        private long logQueueSize;
        private double cacheHitRate;
        private long failoverSwitches;
        private long failoverTerminations;
        private double failoverDepthAvg;
    }

    @Data
    @Builder
    public static class Selection {
        private long saprSelections;
        private long roundRobinSelections;
        private long fallbackToRoundRobin;
        private long skippedExcluded;
        private long skippedCircuitOpen;
        private long skippedCircuitHalfOpen;
        private long bulkheadRejectedNonStream;
        private long bulkheadRejectedStream;
        private long failoverAttemptsNonStream;
        private long failoverAttemptsStream;
    }

    @Data
    @Builder
    public static class LogPipeline {
        private long queueSize;
        private long droppedTotal;
        private double avgBatchSize;
        private double avgFlushMs;
    }

    @Data
    @Builder
    public static class CacheMetric {
        private String cache;
        private long hits;
        private long misses;
        private long expired;
        private long loads;
        private double hitRate;
        private double avgLoadMs;
    }
}
