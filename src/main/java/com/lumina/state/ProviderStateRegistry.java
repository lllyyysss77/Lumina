package com.lumina.state;

import com.lumina.config.CircuitBreakerConfig;
import com.lumina.entity.ProviderRuntimeStats;
import com.lumina.mapper.ProviderRuntimeStatsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderStateRegistry {

    private final ProviderRuntimeStatsMapper mapper;
    private final CircuitBreakerConfig config;
    private final ConcurrentHashMap<String, ProviderRuntimeState> stateMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadFromDb() {
        try {
            List<ProviderRuntimeStats> list = mapper.selectList(null);
            for (ProviderRuntimeStats row : list) {
                ProviderRuntimeState stats = createProviderState(row.getProviderId());
                stats.setProviderName(row.getProviderName());
                stats.setModelName(extractModelName(row.getProviderId()));
                stats.setSuccessRateEma(row.getSuccessRateEma() != null ? row.getSuccessRateEma() : 1.0);
                stats.setLatencyEmaMs(row.getLatencyEmaMs() != null ? row.getLatencyEmaMs() : 0);
                stats.setScore(row.getScore() != null ? row.getScore() : 100);
                stats.getTotalRequests().set(row.getTotalRequests() != null ? row.getTotalRequests() : 0);
                stats.getSuccessRequests().set(row.getSuccessRequests() != null ? row.getSuccessRequests() : 0);
                stats.getFailureRequests().set(row.getFailureRequests() != null ? row.getFailureRequests() : 0);
                stats.getConsecutiveFailures().set(row.getConsecutiveFailures() != null ? row.getConsecutiveFailures() : 0);
                stats.setOpenAttempt(row.getOpenAttempt() != null ? row.getOpenAttempt() : 0);
                stats.setStateSinceAt(resolveStateSinceAt(row));
                recoverCircuitState(stats, row);
                stats.clearDirty();

                stateMap.put(row.getProviderId(), stats);
            }
            log.info("从数据库加载了 {} 条 Provider 运行态数据", list.size());
        } catch (Exception e) {
            if (isTableNotExists(e)) {
                log.warn("provider_runtime_stats 表尚未初始化，跳过运行态加载");
            } else {
                log.error("加载 Provider 运行态数据失败", e);
            }
        }
    }

    /**
     * 创建带配置的 ProviderRuntimeState
     */
    private ProviderRuntimeState createProviderState(String providerId) {
        return new ProviderRuntimeState(
                providerId,
                config.getWindowBucketCount(),
                config.getWindowBucketDurationMs(),
                config.getMaxConcurrentRequestsPerProvider()
        );
    }

    public ProviderRuntimeState get(String providerId) {
        return stateMap.computeIfAbsent(
                providerId,
                this::createProviderState
        );
    }

    public Collection<ProviderRuntimeState> all() {
        return stateMap.values();
    }

    /**
     * 获取 Provider 状态（不自动创建）
     * @param providerId Provider ID
     * @return 状态对象，不存在时返回 null
     */
    public ProviderRuntimeState getIfExists(String providerId) {
        return stateMap.get(providerId);
    }

    /**
     * 获取所有 Provider 状态列表
     */
    public List<ProviderRuntimeState> getAllProviders() {
        return new java.util.ArrayList<>(stateMap.values());
    }

    public void clear() {
        stateMap.clear();
    }

    /**
     * 从注册表中移除指定的 Provider
     * @param providerId Provider ID
     */
    public void remove(String providerId) {
        stateMap.remove(providerId);
    }

    /**
     * 批量移除指定的 Providers
     * @param providerIds Provider IDs
     */
    public void removeAll(Collection<String> providerIds) {
        providerIds.forEach(stateMap::remove);
    }

    private boolean isTableNotExists(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().contains("no such table")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void recoverCircuitState(ProviderRuntimeState stats, ProviderRuntimeStats row) {
        CircuitState persistedState = row.getCircuitState() != null
                ? CircuitState.valueOf(row.getCircuitState())
                : CircuitState.CLOSED;

        if (persistedState == CircuitState.HALF_OPEN) {
            long now = System.currentTimeMillis();
            long nextProbeAt = row.getNextProbeAt() != null && row.getNextProbeAt() > now
                    ? row.getNextProbeAt()
                    : now + config.getOpenBaseMs();

            stats.setCircuitState(CircuitState.OPEN);
            stats.setCircuitOpenedAt(row.getCircuitOpenedAt() != null && row.getCircuitOpenedAt() > 0
                    ? row.getCircuitOpenedAt()
                    : now);
            stats.setNextProbeAt(nextProbeAt);
            stats.recordStateTransition("recovered_half_open_normalized", now);
            log.info("Provider {} 恢复到持久化 HALF_OPEN 状态时已归一化为 OPEN，下次探测时间: {}",
                    row.getProviderId(), nextProbeAt);
            return;
        }

        stats.setCircuitState(persistedState);
        stats.setCircuitOpenedAt(row.getCircuitOpenedAt() != null ? row.getCircuitOpenedAt() : 0);
        stats.setNextProbeAt(row.getNextProbeAt() != null ? row.getNextProbeAt() : 0);
    }

    private long resolveStateSinceAt(ProviderRuntimeStats row) {
        if (row.getCircuitOpenedAt() != null && row.getCircuitOpenedAt() > 0) {
            return row.getCircuitOpenedAt();
        }
        if (row.getUpdatedAt() != null) {
            return row.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return System.currentTimeMillis();
    }

    private String extractModelName(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return providerId;
        }
        int lastUnderscore = providerId.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore + 1 >= providerId.length()) {
            return providerId;
        }
        return providerId.substring(lastUnderscore + 1);
    }
}
