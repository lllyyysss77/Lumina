package com.lumina.metrics;

import com.lumina.state.CircuitState;
import com.lumina.state.ProviderRuntimeState;
import com.lumina.state.ProviderStateRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器 Prometheus 指标收集器
 *
 * 输出指标：
 * - lumina_circuit_state: 熔断状态 (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
 * - lumina_error_rate: 错误率
 * - lumina_slow_rate: 慢调用率
 * - lumina_consecutive_failures: 连续失败次数
 * - lumina_bulkhead_concurrent: 当前并发数
 * - lumina_bulkhead_max: 最大并发数
 * - lumina_bulkhead_rejected_total: 被拒绝的请求总数
 * - lumina_provider_score: Provider 评分
 * - lumina_latency_ema_ms: 延迟 EMA
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerMetrics {

    private final MeterRegistry meterRegistry;
    private final ProviderStateRegistry stateRegistry;

    // 已注册的 Provider ID 集合，避免重复注册
    private final Map<String, Boolean> registeredProviders = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Gauge.builder("lumina_providers_registered", stateRegistry, registry -> registry.all().size())
                .description("Total number of provider runtime states tracked in memory")
                .register(meterRegistry);
        Gauge.builder("lumina_circuit_state_count", stateRegistry,
                        registry -> countByState(CircuitState.CLOSED))
                .tag("state", "closed")
                .description("Number of providers in CLOSED state")
                .register(meterRegistry);
        Gauge.builder("lumina_circuit_state_count", stateRegistry,
                        registry -> countByState(CircuitState.OPEN))
                .tag("state", "open")
                .description("Number of providers in OPEN state")
                .register(meterRegistry);
        Gauge.builder("lumina_circuit_state_count", stateRegistry,
                        registry -> countByState(CircuitState.HALF_OPEN))
                .tag("state", "half_open")
                .description("Number of providers in HALF_OPEN state")
                .register(meterRegistry);
        log.info("CircuitBreakerMetrics 初始化完成");
    }

    /**
     * 定期检查并注册新 Provider 的指标
     */
    @Scheduled(fixedDelay = 5000)
    public void registerMetrics() {
        for (ProviderRuntimeState state : stateRegistry.all()) {
            String providerId = state.getProviderId();

            if (registeredProviders.containsKey(providerId)) {
                continue;
            }

            registerProviderMetrics(state);
            registeredProviders.put(providerId, true);
        }
    }

    /**
     * 为单个 Provider 注册所有指标
     */
    private void registerProviderMetrics(ProviderRuntimeState state) {
        String providerId = state.getProviderId();
        String providerName = state.getProviderName() != null ? state.getProviderName() : "unknown";
        Tags tags = Tags.of("provider_id", sanitizeTag(providerId), "provider_name", sanitizeTag(providerName));

        // 熔断状态 (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
        Gauge.builder("lumina_circuit_state", state, s -> {
            CircuitState circuitState = s.getCircuitState();
            switch (circuitState) {
                case CLOSED: return 0;
                case OPEN: return 1;
                case HALF_OPEN: return 2;
                default: return -1;
            }
        })
        .tags(tags)
        .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
        .register(meterRegistry);

        // 错误率
        Gauge.builder("lumina_error_rate", state, ProviderRuntimeState::getWindowErrorRate)
                .tags(tags)
                .description("Error rate in sliding window")
                .register(meterRegistry);

        // 慢调用率
        Gauge.builder("lumina_slow_rate", state, ProviderRuntimeState::getWindowSlowRate)
                .tags(tags)
                .description("Slow call rate in sliding window")
                .register(meterRegistry);

        // 连续失败次数
        Gauge.builder("lumina_consecutive_failures", state, s -> s.getConsecutiveFailures().get())
                .tags(tags)
                .description("Consecutive failure count")
                .register(meterRegistry);

        // 当前并发数
        Gauge.builder("lumina_bulkhead_concurrent", state, s -> s.getBulkhead().getCurrentConcurrent())
                .tags(tags)
                .description("Current concurrent requests")
                .register(meterRegistry);

        // 最大并发数
        Gauge.builder("lumina_bulkhead_max", state, s -> s.getBulkhead().getMaxConcurrent())
                .tags(tags)
                .description("Maximum concurrent requests")
                .register(meterRegistry);

        // 被拒绝的请求总数
        Gauge.builder("lumina_bulkhead_rejected_total", state, s -> s.getBulkhead().getRejectedCount())
                .tags(tags)
                .description("Total rejected requests due to bulkhead full")
                .register(meterRegistry);

        // Provider 评分
        Gauge.builder("lumina_provider_score", state, ProviderRuntimeState::getScore)
                .tags(tags)
                .description("Provider health score (0-100)")
                .register(meterRegistry);

        // 延迟 EMA
        Gauge.builder("lumina_latency_ema_ms", state, ProviderRuntimeState::getLatencyEmaMs)
                .tags(tags)
                .description("Latency exponential moving average in milliseconds")
                .register(meterRegistry);

        // 总请求数
        Gauge.builder("lumina_total_requests", state, s -> s.getTotalRequests().get())
                .tags(tags)
                .description("Total request count")
                .register(meterRegistry);

        // 成功请求数
        Gauge.builder("lumina_success_requests", state, s -> s.getSuccessRequests().get())
                .tags(tags)
                .description("Successful request count")
                .register(meterRegistry);

        // 失败请求数
        Gauge.builder("lumina_failure_requests", state, s -> s.getFailureRequests().get())
                .tags(tags)
                .description("Failed request count")
                .register(meterRegistry);

        // 熔断次数
        Gauge.builder("lumina_open_attempt", state, ProviderRuntimeState::getOpenAttempt)
                .tags(tags)
                .description("Circuit breaker open attempt count")
                .register(meterRegistry);

        log.debug("已为 Provider {} 注册 Prometheus 指标", providerId);
    }

    /**
     * 清理 tag 值，移除特殊字符
     */
    private String sanitizeTag(String value) {
        if (value == null) {
            return "unknown";
        }
        // 移除或替换可能导致问题的字符
        return value.replaceAll("[^a-zA-Z0-9_\\-./]", "_")
                    .substring(0, Math.min(value.length(), 128));
    }

    private long countByState(CircuitState targetState) {
        return stateRegistry.all().stream()
                .filter(state -> state.getCircuitState() == targetState)
                .count();
    }
}
