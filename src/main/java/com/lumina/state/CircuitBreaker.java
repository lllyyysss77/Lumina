package com.lumina.state;

import org.springframework.stereotype.Component;

@Component
public class CircuitBreaker {

    private static final int FAILURE_THRESHOLD = 5;
    private static final long OPEN_DURATION_MS = 30_000;

    public boolean allowRequest(ProviderRuntimeState stats) {

        if (stats.getCircuitState() == CircuitState.CLOSED) {
            return true;
        }

        if (stats.getCircuitState() == CircuitState.OPEN) {
            if (System.currentTimeMillis() - stats.getCircuitOpenedAt() > OPEN_DURATION_MS) {
                stats.setCircuitState(CircuitState.HALF_OPEN);
                return true; // 允许一次试探
            }
            return false;
        }

        // HALF_OPEN：只允许一次。这里简化处理，实际可以通过 CAS 确保只有一个线程进入 HALF_OPEN
        return true;
    }

    public void onSuccess(ProviderRuntimeState stats) {
        stats.setCircuitState(CircuitState.CLOSED);
        stats.getFailureRequests().set(0);
    }

    public void onFailure(ProviderRuntimeState stats) {
        // 如果评分太低或连续失败次数多（这里简单用 failureRequests 计数，实际可能需要连续失败计数）
        // 这里的 failureRequests 在 onSuccess 会被清零，所以其实可以代表“当前阶段失败次数”
        if (stats.getFailureRequests().get() >= FAILURE_THRESHOLD
                || stats.getScore() < 40) {

            stats.setCircuitState(CircuitState.OPEN);
            stats.setCircuitOpenedAt(System.currentTimeMillis());
        }
    }
}
