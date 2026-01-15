package com.lumina.state;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProviderScoreCalculator {

    private static final double ALPHA = 0.2;
    private static final double LATENCY_SAFE_THRESHOLD_MS = 5000.0; // 5000ms 以内不扣分
    private static final double LATENCY_MAX_THRESHOLD_MS = 30000.0; // 30000ms 以上扣满 20 分

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
        // 1. 延迟惩罚逻辑：惩罚超额部分
        double currentLatency = stats.getLatencyEmaMs();
        double latencyPenalty = 0;

        if (currentLatency > LATENCY_SAFE_THRESHOLD_MS) {
            latencyPenalty = Math.min(
                    (currentLatency - LATENCY_SAFE_THRESHOLD_MS) / (LATENCY_MAX_THRESHOLD_MS - LATENCY_SAFE_THRESHOLD_MS),
                    1.0
            );
        }

        // 2. 失败爆发率：反映短期稳定性
        double failureBurst =
                stats.getFailureRequests().get() * 1.0 /
                Math.max(stats.getTotalRequests().get(), 1);

        // 3. 最终评分计算
        double score =
                stats.getSuccessRateEma() * 70  // 稳定性占大头
              - latencyPenalty * 20             // 延迟只扣除超额部分
              - failureBurst * 10;              // 额外惩罚频繁失败

        stats.setScore(Math.max(0, Math.min(100, score)));
    }
}
