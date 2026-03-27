package com.lumina.state;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 熔断器事件日志记录器
 * 输出结构化 JSON 日志用于可观测性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventLogger {

    private static final int MAX_RECENT_MANUAL_EVENTS = 100;

    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedDeque<ManualControlEvent> recentManualEvents = new ConcurrentLinkedDeque<>();

    /**
     * 熔断状态变更事件
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CircuitStateChangeEvent {
        private String event;
        private String providerId;
        private String providerName;
        private String fromState;
        private String toState;
        private String reason;
        private Double errorRate;
        private Double slowRate;
        private Integer consecutiveFailures;
        private Long openDurationMs;
        private Integer attempt;
        private Integer currentConcurrent;
        private Integer maxConcurrent;
        private String timestamp;
    }

    /**
     * 记录状态转换事件
     */
    public void logStateChange(ProviderRuntimeState state, CircuitState fromState, CircuitState toState, String reason) {
        logStateChange(state, fromState, toState, reason, null);
    }

    /**
     * 记录状态转换事件（带额外信息）
     */
    public void logStateChange(ProviderRuntimeState state, CircuitState fromState, CircuitState toState,
                                String reason, Long openDurationMs) {
        try {
            CircuitStateChangeEvent event = CircuitStateChangeEvent.builder()
                    .event("circuit_state_change")
                    .providerId(state.getProviderId())
                    .providerName(state.getProviderName())
                    .fromState(fromState.name())
                    .toState(toState.name())
                    .reason(reason)
                    .errorRate(roundToTwoDecimals(state.getWindowErrorRate()))
                    .slowRate(roundToTwoDecimals(state.getWindowSlowRate()))
                    .consecutiveFailures(state.getConsecutiveFailures().get())
                    .openDurationMs(openDurationMs)
                    .attempt(toState == CircuitState.OPEN ? state.getOpenAttempt() : null)
                    .currentConcurrent(state.getBulkhead().getCurrentConcurrent())
                    .maxConcurrent(state.getBulkhead().getMaxConcurrent())
                    .timestamp(Instant.now().toString())
                    .build();

            String json = objectMapper.writeValueAsString(event);
            log.info("CIRCUIT_EVENT: {}", json);
        } catch (JsonProcessingException e) {
            log.warn("无法序列化熔断事件日志", e);
            // 降级为简单日志
            log.info("CIRCUIT_EVENT: provider={}, from={}, to={}, reason={}",
                    state.getProviderId(), fromState, toState, reason);
        }
    }

    /**
     * 记录熔断打开事件
     */
    public void logCircuitOpen(ProviderRuntimeState state, String reason, long openDurationMs) {
        logStateChange(state, state.getCircuitState(), CircuitState.OPEN, reason, openDurationMs);
    }

    /**
     * 记录熔断关闭事件
     */
    public void logCircuitClose(ProviderRuntimeState state) {
        logStateChange(state, CircuitState.HALF_OPEN, CircuitState.CLOSED, "half_open_success_threshold_reached");
    }

    /**
     * 记录进入半开状态事件
     */
    public void logHalfOpen(ProviderRuntimeState state) {
        logStateChange(state, CircuitState.OPEN, CircuitState.HALF_OPEN, "probe_time_reached");
    }

    /**
     * 记录舱壁拒绝事件
     */
    public void logBulkheadRejection(ProviderRuntimeState state) {
        try {
            CircuitStateChangeEvent event = CircuitStateChangeEvent.builder()
                    .event("bulkhead_rejection")
                    .providerId(state.getProviderId())
                    .providerName(state.getProviderName())
                    .currentConcurrent(state.getBulkhead().getCurrentConcurrent())
                    .maxConcurrent(state.getBulkhead().getMaxConcurrent())
                    .timestamp(Instant.now().toString())
                    .build();

            String json = objectMapper.writeValueAsString(event);
            log.warn("BULKHEAD_EVENT: {}", json);
        } catch (JsonProcessingException e) {
            log.warn("BULKHEAD_EVENT: provider={}, concurrent={}/{}",
                    state.getProviderId(),
                    state.getBulkhead().getCurrentConcurrent(),
                    state.getBulkhead().getMaxConcurrent());
        }
    }

    /**
     * 记录手动控制事件
     */
    public void logManualControl(ProviderRuntimeState state, CircuitState fromState, CircuitState toState,
                                  String reason, String operator) {
        try {
            ManualControlEvent event = ManualControlEvent.builder()
                    .action("control")
                    .event("manual_circuit_control")
                    .providerId(state.getProviderId())
                    .providerName(state.getProviderName())
                    .modelName(state.getModelName())
                    .fromState(fromState.name())
                    .toState(toState.name())
                    .reason(reason)
                    .operator(operator)
                    .timestamp(Instant.now().toString())
                    .build();

            rememberManualEvent(event);
            String json = objectMapper.writeValueAsString(event);
            log.info("MANUAL_CONTROL_EVENT: {}", json);
        } catch (JsonProcessingException e) {
            log.warn("无法序列化手动控制事件日志", e);
            log.info("MANUAL_CONTROL_EVENT: provider={}, from={}, to={}, reason={}, operator={}",
                    state.getProviderId(), fromState, toState, reason, operator);
        }
    }

    public void logManualRelease(ProviderRuntimeState state, String operator, String reason) {
        try {
            ManualControlEvent event = ManualControlEvent.builder()
                    .action("release")
                    .event("manual_circuit_release")
                    .providerId(state.getProviderId())
                    .providerName(state.getProviderName())
                    .modelName(state.getModelName())
                    .fromState(state.getCircuitState().name())
                    .toState(state.getCircuitState().name())
                    .reason(reason)
                    .operator(operator)
                    .timestamp(Instant.now().toString())
                    .build();
            rememberManualEvent(event);
            String json = objectMapper.writeValueAsString(event);
            log.info("MANUAL_CONTROL_EVENT: {}", json);
        } catch (JsonProcessingException e) {
            log.info("MANUAL_CONTROL_EVENT: provider={}, action=release, operator={}",
                    state.getProviderId(), operator);
        }
    }

    public List<ManualControlEvent> getRecentManualControlEvents(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, MAX_RECENT_MANUAL_EVENTS));
        List<ManualControlEvent> events = new ArrayList<>(cappedLimit);
        int count = 0;
        for (ManualControlEvent event : recentManualEvents) {
            events.add(event);
            count++;
            if (count >= cappedLimit) {
                break;
            }
        }
        return events;
    }

    /**
     * 手动控制事件
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ManualControlEvent {
        private String action;
        private String event;
        private String providerId;
        private String providerName;
        private String modelName;
        private String fromState;
        private String toState;
        private String reason;
        private String operator;
        private String timestamp;
    }

    private void rememberManualEvent(ManualControlEvent event) {
        recentManualEvents.addFirst(event);
        while (recentManualEvents.size() > MAX_RECENT_MANUAL_EVENTS) {
            recentManualEvents.pollLast();
        }
    }

    private Double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
