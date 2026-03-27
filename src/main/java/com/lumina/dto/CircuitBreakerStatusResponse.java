package com.lumina.dto;

import com.lumina.state.CircuitState;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Provider 熔断器状态信息
 */
@Data
@Builder(toBuilder = true)
public class CircuitBreakerStatusResponse {

    private String providerId;
    private String providerName;
    private String modelName;
    private long stateSinceAt;
    private String stateExplanation;
    private String lastStateChangeReason;
    private String lastFailureType;

    // 熔断状态
    private CircuitState circuitState;
    private long circuitOpenedAt;
    private long nextProbeAt;
    private int openAttempt;

    // 统计信息
    private double score;
    private double latencyEmaMs;
    private double successRateEma;
    private double errorRate;
    private double slowRate;
    private long windowTotalCount;
    private int consecutiveFailures;
    private long totalRequests;
    private long successRequests;
    private long failureRequests;

    // 并发舱壁
    private int currentConcurrent;
    private int maxConcurrent;
    private long bulkheadRejectedCount;

    // HALF_OPEN 探测信息
    private int probeRemaining;
    private int halfOpenSuccessCount;
    private int halfOpenFailureCount;

    // 是否为手动控制
    private boolean manuallyControlled;
    private long manualControlledAt;
    private String manualControlOperator;
    private String manualControlReason;

    private String effectiveConfigSource;
    private List<String> effectiveGroupNames;
    private boolean mixedConfig;
    private EffectiveConfigSummary effectiveConfig;

    @Data
    @Builder
    public static class EffectiveConfigSummary {
        private int minCalls;
        private double errorRateThreshold;
        private int consecutiveFailureThreshold;
        private long slowCallThresholdMs;
        private double slowRateThreshold;
        private int permittedCallsInHalfOpen;
        private int halfOpenSuccessThreshold;
        private int halfOpenFailureThreshold;
        private long halfOpenMaxDurationMs;
        private long openBaseMs;
        private long openMaxMs;
        private double backoffMultiplier;
        private double jitterRatio;
        private int maxFailoverAttempts;
        private int maxConcurrentRequestsPerProvider;
        private String sourceLevel;
    }
}
