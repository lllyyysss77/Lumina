package com.lumina.service;

import com.lumina.config.CircuitBreakerConfig;
import com.lumina.config.CircuitBreakerConfigResolver;
import com.lumina.config.EffectiveCircuitBreakerConfig;
import com.lumina.dto.CircuitBreakerControlRequest;
import com.lumina.dto.CircuitBreakerRecentEventDto;
import com.lumina.dto.CircuitBreakerStatusResponse;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.entity.Group;
import com.lumina.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 熔断器管控服务
 * 提供手动控制熔断器状态的能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerManagementService {

    private final ProviderStateRegistry providerStateRegistry;
    private final CircuitBreakerConfig circuitBreakerConfig;
    private final CircuitBreakerConfigResolver configResolver;
    private final GroupService groupService;
    private final CircuitBreakerEventLogger eventLogger;

    /**
     * 获取单个 Provider 的熔断器状态
     */
    public CircuitBreakerStatusResponse getStatus(String providerId) {
        ProviderRuntimeState state = providerStateRegistry.getIfExists(providerId);
        if (state == null) {
            return null;
        }
        return buildStatusResponse(state);
    }

    /**
     * 获取所有 Provider 的熔断器状态列表
     */
    public List<CircuitBreakerStatusResponse> listAllStatus() {
        return providerStateRegistry.getAllProviders()
                .stream()
                .map(this::buildStatusResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取设置中心使用的熔断器管理视图
     */
    public List<CircuitBreakerStatusResponse> listManagementStatus() {
        Map<String, List<ConfigBinding>> bindings = buildConfigBindings();
        return providerStateRegistry.getAllProviders()
                .stream()
                .map(state -> buildStatusResponse(state, resolveConfigBinding(bindings.get(state.getProviderId()))))
                .collect(Collectors.toList());
    }

    public List<CircuitBreakerRecentEventDto> listRecentManualEvents(int limit) {
        return eventLogger.getRecentManualControlEvents(limit)
                .stream()
                .map(event -> CircuitBreakerRecentEventDto.builder()
                        .action(event.getAction())
                        .providerId(event.getProviderId())
                        .providerName(event.getProviderName())
                        .modelName(event.getModelName())
                        .fromState(event.getFromState())
                        .toState(event.getToState())
                        .reason(event.getReason())
                        .operator(event.getOperator())
                        .timestamp(event.getTimestamp())
                        .build())
                .toList();
    }

    /**
     * 手动控制熔断器状态
     */
    public CircuitBreakerStatusResponse controlCircuitBreaker(CircuitBreakerControlRequest request, String operator) {
        String providerId = request.getProviderId();
        ProviderRuntimeState state = providerStateRegistry.get(providerId);

        CircuitState previousState = state.getCircuitState();
        CircuitState targetState = mapTargetState(request.getTargetState());

        String reason = request.getReason() != null ? request.getReason() : "Manual control";

        switch (request.getTargetState()) {
            case OPEN:
                forceOpen(state, request.getDurationMs(), reason, operator);
                break;
            case CLOSED:
                forceClose(state, reason, operator);
                break;
            case HALF_OPEN:
                forceHalfOpen(state, reason, operator);
                break;
        }

        log.info("手动控制熔断器: providerId={}, {} -> {}, operator={}, reason={}",
                providerId, previousState, targetState, operator, reason);

        eventLogger.logManualControl(state, previousState, targetState, reason, operator);

        return buildStatusResponse(state);
    }

    /**
     * 释放手动控制，恢复自动管理
     */
    public CircuitBreakerStatusResponse releaseManualControl(String providerId, String operator) {
        ProviderRuntimeState state = providerStateRegistry.getIfExists(providerId);
        if (state == null) {
            return null;
        }

        String releaseReason = state.getManualControlReason();
        state.disableManualControl();
        state.markDirty();
        state.recordStateTransition(releaseReason != null ? "manual_control_released" : "auto_control_restored", System.currentTimeMillis());
        log.info("释放手动控制: providerId={}, 恢复自动管理", providerId);
        eventLogger.logManualRelease(state, operator, releaseReason);

        return buildStatusResponse(state);
    }

    /**
     * 强制打开熔断器
     */
    private void forceOpen(ProviderRuntimeState state, Long durationMs, String reason, String operator) {
        state.enableManualControl(reason, operator);

        // 计算 OPEN 持续时间
        long openDuration = durationMs != null ? durationMs : circuitBreakerConfig.getOpenBaseMs();
        long changedAt = System.currentTimeMillis();
        long nextProbeAt = changedAt + openDuration;

        state.forceTransitionTo(CircuitState.OPEN);
        state.setCircuitOpenedAt(changedAt);
        state.setNextProbeAt(nextProbeAt);
        state.recordStateTransition(reason, changedAt);
        state.markDirty();

        log.info("强制打开熔断: providerId={}, duration={}ms, nextProbeAt={}",
                state.getProviderId(), openDuration, nextProbeAt);
    }

    /**
     * 强制关闭熔断器
     */
    private void forceClose(ProviderRuntimeState state, String reason, String operator) {
        state.enableManualControl(reason, operator);

        state.forceTransitionTo(CircuitState.CLOSED);
        state.recordStateTransition(reason, System.currentTimeMillis());
        state.resetOnClose();
        state.markDirty();

        log.info("强制关闭熔断: providerId={}", state.getProviderId());
    }

    /**
     * 强制进入 HALF_OPEN 状态
     */
    private void forceHalfOpen(ProviderRuntimeState state, String reason, String operator) {
        state.enableManualControl(reason, operator);

        state.forceTransitionTo(CircuitState.HALF_OPEN);
        state.initHalfOpen(circuitBreakerConfig.getPermittedCallsInHalfOpen());
        state.recordStateTransition(reason, System.currentTimeMillis());
        state.markDirty();

        log.info("强制进入 HALF_OPEN: providerId={}, permittedCalls={}",
                state.getProviderId(), circuitBreakerConfig.getPermittedCallsInHalfOpen());
    }

    /**
     * 转换目标状态枚举
     */
    private CircuitState mapTargetState(CircuitBreakerControlRequest.TargetState targetState) {
        switch (targetState) {
            case OPEN:
                return CircuitState.OPEN;
            case CLOSED:
                return CircuitState.CLOSED;
            case HALF_OPEN:
                return CircuitState.HALF_OPEN;
            default:
                throw new IllegalArgumentException("Unknown target state: " + targetState);
        }
    }

    /**
     * 构建状态响应对象
     */
    private CircuitBreakerStatusResponse buildStatusResponse(ProviderRuntimeState state) {
        return buildStatusResponse(state, null);
    }

    private CircuitBreakerStatusResponse buildStatusResponse(ProviderRuntimeState state, ResolvedConfigBinding resolvedConfig) {
        ProviderBulkhead bulkhead = state.getBulkhead();

        CircuitBreakerStatusResponse base = CircuitBreakerStatusResponse.builder()
                .providerId(state.getProviderId())
                .providerName(state.getProviderName())
                .modelName(resolveModelName(state))
                .stateSinceAt(state.getStateSinceAt())
                .stateExplanation(buildStateExplanation(state))
                .lastStateChangeReason(state.getLastStateChangeReason())
                .lastFailureType(state.getLastFailureType())
                .circuitState(state.getCircuitState())
                .circuitOpenedAt(state.getCircuitOpenedAt())
                .nextProbeAt(state.getNextProbeAt())
                .openAttempt(state.getOpenAttempt())
                .score(state.getScore())
                .latencyEmaMs(state.getLatencyEmaMs())
                .successRateEma(state.getSuccessRateEma())
                .errorRate(state.getWindowErrorRate())
                .slowRate(state.getWindowSlowRate())
                .windowTotalCount(state.getWindowTotalCount())
                .consecutiveFailures(state.getConsecutiveFailures().get())
                .totalRequests(state.getTotalRequests().get())
                .successRequests(state.getSuccessRequests().get())
                .failureRequests(state.getFailureRequests().get())
                .currentConcurrent(bulkhead.getCurrentConcurrent())
                .maxConcurrent(bulkhead.getMaxConcurrent())
                .bulkheadRejectedCount(bulkhead.getRejectedCount())
                .probeRemaining(state.getProbeRemaining().get())
                .halfOpenSuccessCount(state.getHalfOpenSuccessCount().get())
                .halfOpenFailureCount(state.getHalfOpenFailureCount().get())
                .manuallyControlled(state.isManuallyControlled())
                .manualControlledAt(state.getManualControlledAt())
                .manualControlOperator(state.getManualControlOperator())
                .manualControlReason(state.getManualControlReason())
                .build();

        if (resolvedConfig == null) {
            return base;
        }

        return base.toBuilder()
                .effectiveConfigSource(resolvedConfig.source())
                .effectiveGroupNames(resolvedConfig.groupNames())
                .mixedConfig(resolvedConfig.mixed())
                .effectiveConfig(CircuitBreakerStatusResponse.EffectiveConfigSummary.builder()
                        .minCalls(resolvedConfig.config().getMinCalls())
                        .errorRateThreshold(resolvedConfig.config().getErrorRateThreshold())
                        .consecutiveFailureThreshold(resolvedConfig.config().getConsecutiveFailureThreshold())
                        .slowCallThresholdMs(resolvedConfig.config().getSlowCallThresholdMs())
                        .slowRateThreshold(resolvedConfig.config().getSlowRateThreshold())
                        .permittedCallsInHalfOpen(resolvedConfig.config().getPermittedCallsInHalfOpen())
                        .halfOpenSuccessThreshold(resolvedConfig.config().getHalfOpenSuccessThreshold())
                        .halfOpenFailureThreshold(resolvedConfig.config().getHalfOpenFailureThreshold())
                        .halfOpenMaxDurationMs(resolvedConfig.config().getHalfOpenMaxDurationMs())
                        .openBaseMs(resolvedConfig.config().getOpenBaseMs())
                        .openMaxMs(resolvedConfig.config().getOpenMaxMs())
                        .backoffMultiplier(resolvedConfig.config().getBackoffMultiplier())
                        .jitterRatio(resolvedConfig.config().getJitterRatio())
                        .maxFailoverAttempts(resolvedConfig.config().getMaxFailoverAttempts())
                        .maxConcurrentRequestsPerProvider(resolvedConfig.config().getMaxConcurrentRequestsPerProvider())
                        .sourceLevel(resolvedConfig.source())
                        .build())
                .build();
    }

    private String resolveModelName(ProviderRuntimeState state) {
        if (state.getModelName() != null && !state.getModelName().isBlank()) {
            return state.getModelName();
        }
        String providerId = state.getProviderId();
        if (providerId == null || providerId.isBlank()) {
            return providerId;
        }
        int lastUnderscore = providerId.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore + 1 >= providerId.length()) {
            return providerId;
        }
        return providerId.substring(lastUnderscore + 1);
    }

    private String buildStateExplanation(ProviderRuntimeState state) {
        if (state.isManuallyControlled()) {
            if (state.getManualControlReason() != null && !state.getManualControlReason().isBlank()) {
                return "Manual control: " + state.getManualControlReason();
            }
            return "Manual control is active";
        }

        String reason = humanizeReason(state.getLastStateChangeReason());
        return switch (state.getCircuitState()) {
            case OPEN -> reason != null ? "OPEN because " + reason : "OPEN until the next probe window";
            case HALF_OPEN -> state.getProbeRemaining().get() > 0
                    ? "HALF_OPEN probing, remaining probes: " + state.getProbeRemaining().get()
                    : "HALF_OPEN waiting for probe results";
            case CLOSED -> reason != null ? "CLOSED because " + reason : "Serving normally";
        };
    }

    private String humanizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return switch (reason) {
            case "consecutive_failure_threshold_reached" -> "consecutive failures crossed the threshold";
            case "error_rate_threshold_reached" -> "error rate crossed the threshold";
            case "slow_rate_threshold_reached" -> "slow-call rate crossed the threshold";
            case "half_open_failure_threshold_reached" -> "HALF_OPEN failures crossed the threshold";
            case "half_open_success_threshold_reached" -> "HALF_OPEN recovered successfully";
            case "half_open_timeout" -> "HALF_OPEN timed out and reopened";
            case "probe_time_reached" -> "the probe window was reached";
            case "recovered_half_open_normalized" -> "startup normalized persisted HALF_OPEN to OPEN";
            case "manual_control_released" -> "manual control was released";
            case "auto_control_restored" -> "automatic control was restored";
            default -> reason.replace('_', ' ');
        };
    }

    private Map<String, List<ConfigBinding>> buildConfigBindings() {
        Map<String, List<ConfigBinding>> bindings = new LinkedHashMap<>();
        for (Group group : groupService.list()) {
            try {
                ModelGroupConfig config = groupService.getModelGroupConfig(group.getName());
                if (config == null || config.getItems() == null) {
                    continue;
                }
                for (ModelGroupConfigItem item : config.getItems()) {
                    String providerId = generateProviderId(item);
                    EffectiveCircuitBreakerConfig effectiveConfig = configResolver.resolve(
                            config.getId(),
                            config.getCircuitBreakerConfig(),
                            providerId,
                            item.getCircuitBreakerConfig(),
                            0
                    );
                    bindings.computeIfAbsent(providerId, ignored -> new ArrayList<>())
                            .add(new ConfigBinding(config.getName(), effectiveConfig));
                }
            } catch (Exception e) {
                log.warn("加载熔断器配置上下文失败: group={}", group.getName(), e);
            }
        }
        return bindings;
    }

    private ResolvedConfigBinding resolveConfigBinding(List<ConfigBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            EffectiveCircuitBreakerConfig global = configResolver.resolve();
            return new ResolvedConfigBinding(global.getSourceLevel(), Collections.emptyList(), false, global);
        }

        List<String> groupNames = bindings.stream()
                .map(ConfigBinding::groupName)
                .distinct()
                .toList();

        Set<ConfigFingerprint> fingerprints = bindings.stream()
                .map(binding -> new ConfigFingerprint(binding.effectiveConfig()))
                .collect(Collectors.toSet());

        if (fingerprints.size() > 1) {
            return new ResolvedConfigBinding("mixed", groupNames, true, bindings.get(0).effectiveConfig());
        }

        EffectiveCircuitBreakerConfig config = bindings.get(0).effectiveConfig();
        return new ResolvedConfigBinding(config.getSourceLevel(), groupNames, false, config);
    }

    private String generateProviderId(ModelGroupConfigItem item) {
        return String.format("%s_%s_%s",
                item.getBaseUrl(),
                item.getApiKey() != null ? item.getApiKey().hashCode() : "null",
                item.getModelName());
    }

    private record ConfigBinding(String groupName, EffectiveCircuitBreakerConfig effectiveConfig) {}

    private record ResolvedConfigBinding(
            String source,
            List<String> groupNames,
            boolean mixed,
            EffectiveCircuitBreakerConfig config
    ) {}

    private record ConfigFingerprint(
            int minCalls,
            double errorRateThreshold,
            int consecutiveFailureThreshold,
            long slowCallThresholdMs,
            double slowRateThreshold,
            int permittedCallsInHalfOpen,
            int halfOpenSuccessThreshold,
            int halfOpenFailureThreshold,
            long halfOpenMaxDurationMs,
            long openBaseMs,
            long openMaxMs,
            double backoffMultiplier,
            double jitterRatio,
            int maxFailoverAttempts,
            int maxConcurrentRequestsPerProvider,
            String sourceLevel
    ) {
        ConfigFingerprint(EffectiveCircuitBreakerConfig config) {
            this(
                    config.getMinCalls(),
                    config.getErrorRateThreshold(),
                    config.getConsecutiveFailureThreshold(),
                    config.getSlowCallThresholdMs(),
                    config.getSlowRateThreshold(),
                    config.getPermittedCallsInHalfOpen(),
                    config.getHalfOpenSuccessThreshold(),
                    config.getHalfOpenFailureThreshold(),
                    config.getHalfOpenMaxDurationMs(),
                    config.getOpenBaseMs(),
                    config.getOpenMaxMs(),
                    config.getBackoffMultiplier(),
                    config.getJitterRatio(),
                    config.getMaxFailoverAttempts(),
                    config.getMaxConcurrentRequestsPerProvider(),
                    config.getSourceLevel()
            );
        }
    }
}
