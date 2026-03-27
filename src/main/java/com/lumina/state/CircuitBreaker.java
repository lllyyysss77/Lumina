package com.lumina.state;

import com.lumina.config.CircuitBreakerConfig;
import com.lumina.config.EffectiveCircuitBreakerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreaker {

    private final CircuitBreakerConfig globalConfig;
    private final CircuitBreakerEventLogger eventLogger;

    // ========== 使用全局配置的方法（向后兼容） ==========

    /**
     * 判断是否允许请求通过（使用全局配置）
     */
    public boolean allowRequest(ProviderRuntimeState state) {
        return allowRequest(state, EffectiveCircuitBreakerConfig.fromGlobal(globalConfig));
    }

    /**
     * 请求成功回调（使用全局配置）
     */
    public void onSuccess(ProviderRuntimeState state) {
        onSuccess(state, EffectiveCircuitBreakerConfig.fromGlobal(globalConfig));
    }

    /**
     * 请求失败回调（使用全局配置）
     */
    public void onFailure(ProviderRuntimeState state, FailureType failureType) {
        onFailure(state, failureType, EffectiveCircuitBreakerConfig.fromGlobal(globalConfig));
    }

    /**
     * 兼容旧接口
     */
    public void onFailure(ProviderRuntimeState state) {
        onFailure(state, FailureType.UNKNOWN);
    }

    // ========== 使用 EffectiveConfig 的方法（支持动态配置） ==========

    /**
     * 判断是否允许请求通过
     * @param state Provider 运行态
     * @param config 生效配置
     * @return 是否允许
     */
    public boolean allowRequest(ProviderRuntimeState state, EffectiveCircuitBreakerConfig config) {
        CircuitState currentState = state.getCircuitState();

        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                return handleOpenState(state, config);
            case HALF_OPEN:
                return handleHalfOpenState(state, config);
            default:
                return true;
        }
    }

    /**
     * 请求成功回调
     * @param state Provider 运行态
     * @param config 生效配置
     */
    public void onSuccess(ProviderRuntimeState state, EffectiveCircuitBreakerConfig config) {
        // 清除连续失败计数
        state.clearConsecutiveFailures();
        state.markDirty();

        CircuitState currentState = state.getCircuitState();

        if (currentState == CircuitState.HALF_OPEN) {
            int successCount = state.incrementHalfOpenSuccess();
            log.debug("Provider {} HALF_OPEN 探测成功，成功计数: {}/{}",
                    state.getProviderId(), successCount, config.getHalfOpenSuccessThreshold());

            // 如果手动控制，不自动关闭熔断
            if (state.isManuallyControlled()) {
                log.debug("Provider {} 处于手动控制模式，不自动关闭熔断", state.getProviderId());
                return;
            }

            if (successCount >= config.getHalfOpenSuccessThreshold()) {
                // 达到成功阈值，关闭熔断
                if (state.tryTransitionTo(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                    state.recordStateTransition("half_open_success_threshold_reached", System.currentTimeMillis());
                    state.resetOnClose();
                    eventLogger.logCircuitClose(state);
                    log.info("Provider {} 熔断器关闭，恢复正常服务", state.getProviderId());
                }
            }
        }
    }

    /**
     * 请求失败回调
     * @param state Provider 运行态
     * @param failureType 错误类型
     * @param config 生效配置
     */
    public void onFailure(ProviderRuntimeState state, FailureType failureType, EffectiveCircuitBreakerConfig config) {
        state.recordFailureType(failureType.name());
        // 如果错误类型不计入熔断，直接返回
        if (!failureType.countsAsFailure()) {
            log.debug("Provider {} 错误类型 {} 不计入熔断统计", state.getProviderId(), failureType);
            return;
        }

        state.markDirty();

        CircuitState currentState = state.getCircuitState();

        if (currentState == CircuitState.HALF_OPEN) {
            int failureCount = state.incrementHalfOpenFailure();
            log.debug("Provider {} HALF_OPEN 探测失败，失败计数: {}/{}",
                    state.getProviderId(), failureCount, config.getHalfOpenFailureThreshold());

            if (failureCount >= config.getHalfOpenFailureThreshold()) {
                // 达到失败阈值，重新打开熔断
                tripCircuit(state, "half_open_failure_threshold_reached", config);
            }
        } else if (currentState == CircuitState.CLOSED) {
            handleClosedStateFailure(state, config);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 处理 OPEN 状态
     */
    private boolean handleOpenState(ProviderRuntimeState state, EffectiveCircuitBreakerConfig config) {
        // 如果手动控制，不自动转换状态
        if (state.isManuallyControlled()) {
            log.debug("Provider {} 处于手动控制模式，不自动转换到 HALF_OPEN", state.getProviderId());
            return false;
        }

        long now = System.currentTimeMillis();
        long nextProbeAt = state.getNextProbeAt();

        if (now < nextProbeAt) {
            return false;
        }

        // 尝试 CAS 转换到 HALF_OPEN
        if (state.tryTransitionTo(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
            state.initHalfOpen(config.getPermittedCallsInHalfOpen());
            state.recordStateTransition("probe_time_reached", System.currentTimeMillis());
            state.markDirty();
            eventLogger.logHalfOpen(state);
            log.info("Provider {} 熔断器进入 HALF_OPEN 状态，允许 {} 个探测请求",
                    state.getProviderId(), config.getPermittedCallsInHalfOpen());
            return state.tryAcquireProbe();
        }

        // CAS 失败，检查是否能获取探测配额
        if (state.getCircuitState() == CircuitState.HALF_OPEN) {
            return state.tryAcquireProbe();
        }

        return false;
    }

    /**
     * 处理 HALF_OPEN 状态
     */
    private boolean handleHalfOpenState(ProviderRuntimeState state, EffectiveCircuitBreakerConfig config) {
        // 检查 HALF_OPEN 是否超时
        if (state.isHalfOpenTimedOut(config.getHalfOpenMaxDurationMs())) {
            log.warn("Provider {} HALF_OPEN 状态超时（{}ms），转回 OPEN",
                    state.getProviderId(), config.getHalfOpenMaxDurationMs());
            tripCircuit(state, "half_open_timeout", config);
            return false;
        }

        // 检查是否有探测配额
        if (state.tryAcquireProbe()) {
            return true;
        }

        return false;
    }

    /**
     * 处理 CLOSED 状态下的失败
     */
    private void handleClosedStateFailure(ProviderRuntimeState state, EffectiveCircuitBreakerConfig config) {
        // 增加连续失败计数
        int consecutiveFailures = state.incrementConsecutiveFailures();

        // 检查连续失败阈值
        if (consecutiveFailures >= config.getConsecutiveFailureThreshold()) {
            log.warn("Provider {} 连续失败 {} 次，触发熔断",
                    state.getProviderId(), consecutiveFailures);
            tripCircuit(state, "consecutive_failure_threshold_reached", config);
            return;
        }

        // 检查错误率（需要足够的请求数）
        long windowTotal = state.getWindowTotalCount();
        if (windowTotal >= config.getMinCalls()) {
            double errorRate = state.getWindowErrorRate();

            if (errorRate >= config.getErrorRateThreshold()) {
                log.warn("Provider {} 错误率 {:.2f}% 超过阈值 {:.2f}%，触发熔断",
                        state.getProviderId(),
                        errorRate * 100,
                        config.getErrorRateThreshold() * 100);
                tripCircuit(state, "error_rate_threshold_reached", config);
                return;
            }

            // 检查慢调用率
            double slowRate = state.getWindowSlowRate();
            if (slowRate >= config.getSlowRateThreshold()) {
                log.warn("Provider {} 慢调用率 {:.2f}% 超过阈值 {:.2f}%，触发熔断",
                        state.getProviderId(),
                        slowRate * 100,
                        config.getSlowRateThreshold() * 100);
                tripCircuit(state, "slow_rate_threshold_reached", config);
            }
        }
    }

    /**
     * 触发熔断（打开熔断器）
     */
    private void tripCircuit(ProviderRuntimeState state, String reason, EffectiveCircuitBreakerConfig config) {
        // 如果手动控制，不自动触发熔断
        if (state.isManuallyControlled()) {
            log.debug("Provider {} 处于手动控制模式，不自动触发熔断", state.getProviderId());
            return;
        }

        CircuitState currentState = state.getCircuitState();

        if (currentState == CircuitState.OPEN) {
            return;
        }

        if (state.tryTransitionTo(currentState, CircuitState.OPEN)) {
            int attempt = state.getOpenAttempt() + 1;
            state.setOpenAttempt(attempt);

            long openDuration = calculateOpenDuration(attempt, config);
            long changedAt = System.currentTimeMillis();
            long nextProbeAt = changedAt + openDuration;
            state.setNextProbeAt(nextProbeAt);
            state.setCircuitOpenedAt(changedAt);
            state.recordStateTransition(reason, changedAt);
            state.markDirty();

            eventLogger.logStateChange(state, currentState, CircuitState.OPEN, reason, openDuration);

            log.warn("Provider {} 熔断器打开，第 {} 次熔断，退避时间: {}ms，下次探测: {}",
                    state.getProviderId(), attempt, openDuration, nextProbeAt);
        }
    }

    /**
     * 计算 OPEN 状态持续时间（指数退避 + Jitter）
     */
    long calculateOpenDuration(int attempt, EffectiveCircuitBreakerConfig config) {
        double baseDuration = config.getOpenBaseMs() *
                Math.pow(config.getBackoffMultiplier(), attempt - 1);

        baseDuration = Math.min(baseDuration, config.getOpenMaxMs());

        double jitter = baseDuration * config.getJitterRatio();
        double randomJitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitter;

        long finalDuration = (long) (baseDuration + randomJitter);

        return Math.max(config.getOpenBaseMs() / 2, Math.min(finalDuration, config.getOpenMaxMs()));
    }

    /**
     * 获取全局配置
     */
    public CircuitBreakerConfig getGlobalConfig() {
        return globalConfig;
    }
}
