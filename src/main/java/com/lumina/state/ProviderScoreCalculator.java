package com.lumina.state;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProviderScoreCalculator {

    private static final double ALPHA = 0.2;

    public void update(ProviderRuntimeState stats, boolean success, long latencyMs) {
        stats.getTotalRequests().incrementAndGet();

        if (success) {
            stats.getSuccessRequests().incrementAndGet();
        } else {
            stats.getFailureRequests().incrementAndGet();
        }

        // 延迟 EMA
        double oldLatency = stats.getLatencyEmaMs();
        stats.setLatencyEmaMs(
                oldLatency == 0
                        ? latencyMs
                        : ALPHA * latencyMs + (1 - ALPHA) * oldLatency
        );

        // 成功率 EMA
        double currentSuccess = success ? 1.0 : 0.0;
        stats.setSuccessRateEma(
                ALPHA * currentSuccess + (1 - ALPHA) * stats.getSuccessRateEma()
        );

        recalcScore(stats);
    }

    private void recalcScore(ProviderRuntimeState stats) {
        double latencyPenalty = Math.min(stats.getLatencyEmaMs() / 3000.0, 1.0);
        double failureBurst =
                stats.getFailureRequests().get() * 1.0 /
                Math.max(stats.getTotalRequests().get(), 1);

        double score =
                stats.getSuccessRateEma() * 70
              - latencyPenalty * 20
              - failureBurst * 10;

        stats.setScore(Math.max(0, Math.min(100, score)));
    }
}
