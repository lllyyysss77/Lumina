package com.lumina.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RelayMetrics {

    private final MeterRegistry meterRegistry;
    private final DistributionSummary failoverDepthSummary;

    public RelayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.failoverDepthSummary = DistributionSummary.builder("lumina_failover_depth")
                .description("Failover depth before a request succeeds or terminates")
                .register(meterRegistry);
    }

    public void recordSelection(String strategy) {
        meterRegistry.counter("lumina_provider_selection_total", "strategy", strategy).increment();
    }

    public void recordProviderSkipped(String reason) {
        meterRegistry.counter("lumina_provider_skipped_total", "reason", reason).increment();
    }

    public void recordFallbackToRoundRobin() {
        meterRegistry.counter("lumina_provider_fallback_total", "strategy", "round_robin").increment();
    }

    public void recordFailoverAttempt(boolean stream, int attemptCount) {
        meterRegistry.counter("lumina_failover_attempts_total",
                "stream", Boolean.toString(stream),
                "attempt", Integer.toString(Math.max(1, attemptCount)))
                .increment();
    }

    public void recordFailoverSwitch(boolean stream, String stage, String failureType) {
        meterRegistry.counter("lumina_failover_switch_total",
                "stream", Boolean.toString(stream),
                "stage", stage,
                "failure_type", failureType)
                .increment();
    }

    public void recordBulkheadRejection(boolean stream) {
        meterRegistry.counter("lumina_bulkhead_rejections_total",
                "stream", Boolean.toString(stream))
                .increment();
    }

    public void recordMaxFailoverExceeded(boolean stream) {
        meterRegistry.counter("lumina_failover_terminated_total",
                "stream", Boolean.toString(stream),
                "reason", "max_attempts")
                .increment();
    }

    public void recordNoProviderAvailable(boolean stream) {
        meterRegistry.counter("lumina_failover_terminated_total",
                "stream", Boolean.toString(stream),
                "reason", "no_provider")
                .increment();
    }

    public void recordFailoverDepth(int depth) {
        failoverDepthSummary.record(Math.max(0, depth));
    }
}
