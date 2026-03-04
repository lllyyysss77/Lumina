package com.lumina.state;

/**
 * 错误类型枚举，控制熔断和 Failover 行为
 */
public enum FailureType {

    SUCCESS(false, false),

    // 强信号，触发熔断 + Failover
    TIMEOUT(true, true),
    CONNECT(true, true),
    DNS(true, true),
    TLS(true, true),
    HTTP_5XX(true, true),

    // 限流，可 Failover
    HTTP_429(true, true),

    // 客户端错误，计入熔断，可 Failover
    HTTP_4XX(true, true),

    // 解码错误，计入熔断，可 Failover
    DECODE(true, true),

    // 未知错误，默认触发熔断和 Failover
    UNKNOWN(true, true);

    private final boolean countsAsFailure;
    private final boolean shouldFailover;

    FailureType(boolean countsAsFailure, boolean shouldFailover) {
        this.countsAsFailure = countsAsFailure;
        this.shouldFailover = shouldFailover;
    }

    /**
     * 是否计入熔断统计
     */
    public boolean countsAsFailure() {
        return countsAsFailure;
    }

    /**
     * 是否应切换 Provider
     */
    public boolean shouldFailover() {
        return shouldFailover;
    }
}
