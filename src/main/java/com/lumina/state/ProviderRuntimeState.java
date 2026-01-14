package com.lumina.state;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class ProviderRuntimeState {

    private final String providerId;

    private final int initialWeight;

    private final AtomicLong successCount = new AtomicLong(0);

    private final AtomicLong failureCount = new AtomicLong(0);

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    private volatile boolean circuitOpen = false;

    private volatile long circuitOpenUntil = 0L;

    private volatile int currentWeight;

    public ProviderRuntimeState(String providerId, int initialWeight) {
        this.providerId = providerId;
        this.initialWeight = initialWeight;
        this.currentWeight = initialWeight;
    }

    public boolean isAvailable() {
        if (!circuitOpen) {
            return true;
        }

        if (System.currentTimeMillis() > circuitOpenUntil) {
            circuitOpen = false;
            consecutiveFailures.set(0);
            return true;
        }

        return false;
    }

    public void recordSuccess() {
        successCount.incrementAndGet();
        consecutiveFailures.set(0);

        currentWeight = Math.min(currentWeight + 1, 10);
    }

    public void recordFailure() {
        failureCount.incrementAndGet();
        int fails = consecutiveFailures.incrementAndGet();

        currentWeight = Math.max(0, currentWeight - 1);

        if (fails >= 3) {
            openCircuit();
        }
    }

    private void openCircuit() {
        circuitOpen = true;
        circuitOpenUntil = System.currentTimeMillis() + 30_000;
    }

    public double getSuccessRate() {
        long total = successCount.get() + failureCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successCount.get() / total;
    }
}