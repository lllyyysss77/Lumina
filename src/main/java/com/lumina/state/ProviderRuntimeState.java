package com.lumina.state;

import lombok.Data;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ProviderRuntimeState {

    private final String providerId;
    private volatile String providerName;
    
    // 统计窗口
    private long windowStart = System.currentTimeMillis();

    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successRequests = new AtomicInteger(0);
    private final AtomicInteger failureRequests = new AtomicInteger(0);

    // 延迟 EMA（指数滑动平均）
    private volatile double latencyEmaMs = 0;

    // 成功率 EMA
    private volatile double successRateEma = 1.0;

    // 当前评分（0 ~ 100）
    private volatile double score = 100;

    // 熔断状态
    private volatile CircuitState circuitState = CircuitState.CLOSED;
    private volatile long circuitOpenedAt = 0;

    public ProviderRuntimeState(String providerId) {
        this.providerId = providerId;
    }

    // 为了兼容现有代码，保留一些存根方法或进行重构
    @Deprecated
    public boolean isAvailable() {
        return circuitState != CircuitState.OPEN;
    }

    @Deprecated
    public int getCurrentWeight() {
        return (int) score;
    }
}
