package com.lumina.state;

import com.lumina.config.CircuitBreakerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Queue;

@Component
@RequiredArgsConstructor
public class ProviderScoreCalculator {

    private final CircuitBreakerConfig config;

    private static final double ALPHA = 0.4;
    private static final double LATENCY_SAFE_THRESHOLD_MS = 5000.0;
    private static final double LATENCY_MAX_THRESHOLD_MS = 30000.0;
    private static final int MIN_REQUESTS_FOR_SCORE = 15;  // 最少请求数

    /**
     * 更新统计数据（带错误类型）
     * @param stats Provider 运行态
     * @param failureType 错误类型
     * @param latencyMs 延迟（毫秒）
     */
    public void update(ProviderRuntimeState stats, FailureType failureType, long latencyMs) {
        boolean success = (failureType == FailureType.SUCCESS);
        boolean isSlow = latencyMs >= config.getSlowCallThresholdMs();
        update(stats, success, latencyMs, isSlow);
    }

    /**
     * 更新统计数据（兼容旧接口）
     * @param stats Provider 运行态
     * @param success 是否成功
     * @param latencyMs 延迟（毫秒）
     */
    public void update(ProviderRuntimeState stats, boolean success, long latencyMs) {
        boolean isSlow = latencyMs >= config.getSlowCallThresholdMs();
        update(stats, success, latencyMs, isSlow);
    }

    /**
     * 更新统计数据（完整版本）
     * @param stats Provider 运行态
     * @param success 是否成功
     * @param latencyMs 延迟（毫秒）
     * @param isSlow 是否为慢调用
     */
    private void update(ProviderRuntimeState stats, boolean success, long latencyMs, boolean isSlow) {
        updateBasicStats(stats, success, latencyMs, isSlow);

        // HALF_OPEN 特殊处理
        if (stats.getCircuitState() == CircuitState.HALF_OPEN) {
            handleHalfOpenScore(stats, success);
            return;
        }

        recalcScore(stats);
    }

    private void handleHalfOpenScore(ProviderRuntimeState stats, boolean success) {
        if (success) {
            // 试探成功，给予恢复机会
            stats.setScore(60.0);
            stats.setSuccessRateEma(0.8);
        } else {
            // 试探失败，保持低分
            stats.setScore(5.0);
        }
        stats.markDirty();
    }

    private void updateBasicStats(ProviderRuntimeState stats, boolean success, long latencyMs, boolean isSlow) {
        stats.getTotalRequests().incrementAndGet();

        if (success) {
            stats.getSuccessRequests().incrementAndGet();
        } else {
            stats.getFailureRequests().incrementAndGet();
        }

        // 更新高性能滑动窗口（Phase 2）
        stats.recordToWindow(success, isSlow);

        // 同时更新旧的滑动窗口（兼容性）
        updateLegacySlidingWindow(stats, success);

        // 延迟 EMA
        double oldLatency = stats.getLatencyEmaMs();
        stats.setLatencyEmaMs(
                oldLatency == 0 ? latencyMs : ALPHA * latencyMs + (1 - ALPHA) * oldLatency
        );

        // 成功率 EMA
        double currentSuccess = success ? 1.0 : 0.0;
        double oldSuccessRate = stats.getSuccessRateEma();
        stats.setSuccessRateEma(
                oldSuccessRate == 0 && stats.getTotalRequests().get() == 1
                        ? currentSuccess
                        : ALPHA * currentSuccess + (1 - ALPHA) * oldSuccessRate
        );
        stats.markDirty();
    }

    @SuppressWarnings("deprecation")
    private void updateLegacySlidingWindow(ProviderRuntimeState stats, boolean success) {
        Queue<Boolean> window = stats.getRecentResults();
        window.offer(success);
        if (window.size() > 20) {
            window.poll();
        }
    }

    private void recalcScore(ProviderRuntimeState stats) {
        long total = stats.getTotalRequests().get();

        // 请求数不足时的策略
        if (total < MIN_REQUESTS_FOR_SCORE) {
            long failures = stats.getFailureRequests().get();
            if (failures == 0) {
                stats.setScore(80.0);
            } else if (failures >= total) {
                stats.setScore(20.0);  // 全失败也不给0分
            } else {
                stats.setScore(50.0);  // 有成功有失败
            }
            return;
        }

        // 1. 延迟惩罚
        double currentLatency = stats.getLatencyEmaMs();
        double latencyPenalty = 0;

        if (currentLatency > LATENCY_SAFE_THRESHOLD_MS) {
            latencyPenalty = Math.min(
                    (currentLatency - LATENCY_SAFE_THRESHOLD_MS) /
                            (LATENCY_MAX_THRESHOLD_MS - LATENCY_SAFE_THRESHOLD_MS),
                    1.0
            );
        }

        // 2. 从新的滑动窗口获取错误率（O(1) 操作，替代旧的 stream 遍历）
        double recentErrorRate = stats.getWindowErrorRate();

        // 3. 慢调用率惩罚（Phase 2 新增）
        double slowRate = stats.getWindowSlowRate();
        double slowPenalty = slowRate * 0.5;  // 慢调用率的一半作为惩罚

        // 4. 最终评分
        double score =
                stats.getSuccessRateEma() * 70
                        - latencyPenalty * 20
                        - recentErrorRate * 10
                        - slowPenalty * 10;

        stats.setScore(Math.max(1.0, Math.min(100, score)));
        stats.markDirty();
    }
}
