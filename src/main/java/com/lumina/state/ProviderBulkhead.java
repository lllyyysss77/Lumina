package com.lumina.state;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Provider 并发舱壁
 * 限制单个 Provider 的最大并发请求数，防止单点过载
 */
public class ProviderBulkhead {

    private final AtomicInteger currentConcurrent = new AtomicInteger(0);
    private final AtomicInteger maxConcurrent;

    // 指标统计
    private final LongAdder rejectedCount = new LongAdder();
    private final LongAdder acquiredCount = new LongAdder();

    /**
     * 构造函数
     * @param maxConcurrent 最大并发数
     */
    public ProviderBulkhead(int maxConcurrent) {
        this.maxConcurrent = new AtomicInteger(Math.max(1, maxConcurrent));
    }

    /**
     * 尝试获取并发许可
     * @return true 如果获取成功，false 如果已达上限
     */
    public boolean tryAcquire() {
        while (true) {
            int current = currentConcurrent.get();
            int max = maxConcurrent.get();
            if (current >= max) {
                rejectedCount.increment();
                return false;  // 快速失败
            }
            if (currentConcurrent.compareAndSet(current, current + 1)) {
                acquiredCount.increment();
                return true;
            }
            // CAS 失败，重试
        }
    }

    /**
     * 释放并发许可
     */
    public void release() {
        int newValue = currentConcurrent.decrementAndGet();
        if (newValue < 0) {
            // 安全保护：不应发生，但如果发生则修正
            currentConcurrent.compareAndSet(newValue, 0);
        }
    }

    /**
     * 获取当前并发数
     */
    public int getCurrentConcurrent() {
        return currentConcurrent.get();
    }

    /**
     * 获取最大并发数
     */
    public int getMaxConcurrent() {
        return maxConcurrent.get();
    }

    /**
     * 动态更新最大并发数
     */
    public void setMaxConcurrent(int newMaxConcurrent) {
        maxConcurrent.set(Math.max(1, newMaxConcurrent));
    }

    /**
     * 获取被拒绝的请求总数
     */
    public long getRejectedCount() {
        return rejectedCount.sum();
    }

    /**
     * 获取成功获取许可的请求总数
     */
    public long getAcquiredCount() {
        return acquiredCount.sum();
    }

    /**
     * 获取剩余可用许可数
     */
    public int getAvailablePermits() {
        return Math.max(0, maxConcurrent.get() - currentConcurrent.get());
    }

    /**
     * 是否已满
     */
    public boolean isFull() {
        return currentConcurrent.get() >= maxConcurrent.get();
    }

    /**
     * 重置统计指标（不影响当前并发数）
     */
    public void resetMetrics() {
        rejectedCount.reset();
        acquiredCount.reset();
    }
}
