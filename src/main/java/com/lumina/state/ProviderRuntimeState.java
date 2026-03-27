package com.lumina.state;

import lombok.Data;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Data
public class ProviderRuntimeState {

    private final String providerId;
    private volatile String providerName;
    private volatile String modelName;
    private volatile long stateSinceAt = System.currentTimeMillis();
    private volatile String lastStateChangeReason;
    private volatile String lastFailureType;

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

    // ========== 熔断状态（CAS 安全） ==========

    // 使用 AtomicReference 保证状态转换的原子性
    private final AtomicReference<CircuitState> circuitStateRef = new AtomicReference<>(CircuitState.CLOSED);

    // 兼容旧代码的 getter/setter
    public CircuitState getCircuitState() {
        return circuitStateRef.get();
    }

    public void setCircuitState(CircuitState state) {
        circuitStateRef.set(state);
    }

    // 熔断打开时间
    private volatile long circuitOpenedAt = 0;

    // ========== HALF_OPEN 探测相关 ==========

    // 探测配额剩余
    private final AtomicInteger probeRemaining = new AtomicInteger(0);

    // HALF_OPEN 成功计数
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);

    // HALF_OPEN 失败计数
    private final AtomicInteger halfOpenFailureCount = new AtomicInteger(0);

    // HALF_OPEN 进入时间（用于超时检测）
    private volatile long halfOpenEnteredAt = 0;

    // ========== 退避相关 ==========

    // 当前退避次数（用于指数退避计算）
    private volatile int openAttempt = 0;

    // 下次允许探测的时间
    private volatile long nextProbeAt = 0;

    // ========== 连续失败计数 ==========

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // ========== 手动控制相关 ==========

    // 是否处于手动控制模式
    private volatile boolean manuallyControlled = false;

    // 手动控制原因（用于审计）
    private volatile String manualControlReason;

    // 手动控制操作人（可选）
    private volatile String manualControlOperator;

    // 手动控制时间
    private volatile long manualControlledAt = 0;

    // ========== 高性能滑动窗口（Phase 2） ==========

    // 使用环形桶实现的滑动窗口指标（替代 recentResults）
    private final SlidingWindowMetrics slidingWindowMetrics;

    // ========== 并发舱壁（Phase 2） ==========

    // Provider 级别并发控制
    private final ProviderBulkhead bulkhead;

    // 脏标记：仅脏状态参与批量落盘
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /**
     * 构造函数（默认配置）
     */
    public ProviderRuntimeState(String providerId) {
        this(providerId, 10, 1000, 50);
    }

    /**
     * 构造函数（自定义配置）
     * @param providerId Provider ID
     * @param windowBucketCount 滑动窗口桶数量
     * @param windowBucketDurationMs 每桶时间跨度（毫秒）
     * @param maxConcurrent 最大并发数
     */
    public ProviderRuntimeState(String providerId, int windowBucketCount, long windowBucketDurationMs, int maxConcurrent) {
        this.providerId = providerId;
        this.slidingWindowMetrics = new SlidingWindowMetrics(windowBucketCount, windowBucketDurationMs);
        this.bulkhead = new ProviderBulkhead(maxConcurrent);
    }

    // 滑动窗口：存储最近N次请求的结果（保留用于兼容，建议使用 slidingWindowMetrics）
    @Deprecated
    private final Queue<Boolean> recentResults = new ConcurrentLinkedQueue<>();

    /**
     * 记录请求结果到滑动窗口
     * @param success 是否成功
     * @param isSlow 是否为慢调用
     */
    public void recordToWindow(boolean success, boolean isSlow) {
        slidingWindowMetrics.record(success, isSlow);
    }

    /**
     * 获取滑动窗口错误率
     */
    public double getWindowErrorRate() {
        return slidingWindowMetrics.getErrorRate();
    }

    /**
     * 获取滑动窗口慢调用率
     */
    public double getWindowSlowRate() {
        return slidingWindowMetrics.getSlowRate();
    }

    /**
     * 获取滑动窗口总请求数
     */
    public long getWindowTotalCount() {
        return slidingWindowMetrics.getTotalCount();
    }

    /**
     * CAS 状态转换
     * @param expected 期望的当前状态
     * @param target 目标状态
     * @return 转换是否成功
     */
    public boolean tryTransitionTo(CircuitState expected, CircuitState target) {
        return circuitStateRef.compareAndSet(expected, target);
    }

    /**
     * 初始化 HALF_OPEN 状态
     * @param permittedCalls 允许的探测请求数
     */
    public void initHalfOpen(int permittedCalls) {
        probeRemaining.set(permittedCalls);
        halfOpenSuccessCount.set(0);
        halfOpenFailureCount.set(0);
        halfOpenEnteredAt = System.currentTimeMillis();
    }

    /**
     * 检查 HALF_OPEN 是否超时
     * @param maxDurationMs 最大持续时间
     * @return 是否超时
     */
    public boolean isHalfOpenTimedOut(long maxDurationMs) {
        if (halfOpenEnteredAt == 0) {
            return false;
        }
        return System.currentTimeMillis() - halfOpenEnteredAt > maxDurationMs;
    }

    /**
     * 尝试获取探测配额
     * @return 是否获取成功
     */
    public boolean tryAcquireProbe() {
        while (true) {
            int current = probeRemaining.get();
            if (current <= 0) {
                return false;
            }
            if (probeRemaining.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    /**
     * 熔断关闭时重置状态
     */
    public void resetOnClose() {
        openAttempt = 0;
        consecutiveFailures.set(0);
        halfOpenSuccessCount.set(0);
        halfOpenFailureCount.set(0);
        halfOpenEnteredAt = 0;
        probeRemaining.set(0);
        slidingWindowMetrics.reset();
    }

    /**
     * 增加连续失败计数
     * @return 增加后的值
     */
    public int incrementConsecutiveFailures() {
        return consecutiveFailures.incrementAndGet();
    }

    /**
     * 清除连续失败计数
     */
    public void clearConsecutiveFailures() {
        consecutiveFailures.set(0);
    }

    /**
     * 增加 HALF_OPEN 成功计数
     * @return 增加后的值
     */
    public int incrementHalfOpenSuccess() {
        return halfOpenSuccessCount.incrementAndGet();
    }

    /**
     * 增加 HALF_OPEN 失败计数
     * @return 增加后的值
     */
    public int incrementHalfOpenFailure() {
        return halfOpenFailureCount.incrementAndGet();
    }

    // 为了兼容现有代码，保留一些存根方法或进行重构
    @Deprecated
    public boolean isAvailable() {
        return circuitStateRef.get() != CircuitState.OPEN;
    }

    @Deprecated
    public int getCurrentWeight() {
        return (int) score;
    }

    // ========== 手动控制方法 ==========

    /**
     * 启用手动控制模式
     * @param reason 控制原因
     * @param operator 操作人
     */
    public void enableManualControl(String reason, String operator) {
        this.manuallyControlled = true;
        this.manualControlReason = reason;
        this.manualControlOperator = operator;
        this.manualControlledAt = System.currentTimeMillis();
    }

    /**
     * 禁用手动控制模式（恢复自动管理）
     */
    public void disableManualControl() {
        this.manuallyControlled = false;
        this.manualControlReason = null;
        this.manualControlOperator = null;
        this.manualControlledAt = 0;
    }

    /**
     * 强制设置熔断状态（用于手动控制）
     * @param targetState 目标状态
     */
    public void forceTransitionTo(CircuitState targetState) {
        circuitStateRef.set(targetState);
    }

    public void recordStateTransition(String reason, long changedAt) {
        this.stateSinceAt = changedAt;
        this.lastStateChangeReason = reason;
    }

    public void recordFailureType(String failureType) {
        this.lastFailureType = failureType;
    }

    public void markDirty() {
        dirty.set(true);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void clearDirty() {
        dirty.set(false);
    }
}
