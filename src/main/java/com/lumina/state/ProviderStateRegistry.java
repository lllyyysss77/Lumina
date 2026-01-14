package com.lumina.state;

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
    private final ConcurrentHashMap<String, ProviderRuntimeState> stateMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadFromDb() {
        try {
            List<ProviderRuntimeStats> list = mapper.selectList(null);
            for (ProviderRuntimeStats row : list) {
                ProviderRuntimeState stats = new ProviderRuntimeState(row.getProviderId());
                stats.setProviderName(row.getProviderName());
                stats.setSuccessRateEma(row.getSuccessRateEma() != null ? row.getSuccessRateEma() : 1.0);
                stats.setLatencyEmaMs(row.getLatencyEmaMs() != null ? row.getLatencyEmaMs() : 0);
                stats.setScore(row.getScore() != null ? row.getScore() : 100);
                stats.getTotalRequests().set(row.getTotalRequests() != null ? row.getTotalRequests() : 0);
                stats.getSuccessRequests().set(row.getSuccessRequests() != null ? row.getSuccessRequests() : 0);
                stats.getFailureRequests().set(row.getFailureRequests() != null ? row.getFailureRequests() : 0);
                stats.setCircuitState(row.getCircuitState() != null ? CircuitState.valueOf(row.getCircuitState()) : CircuitState.CLOSED);
                stats.setCircuitOpenedAt(row.getCircuitOpenedAt() != null ? row.getCircuitOpenedAt() : 0);
                
                stateMap.put(row.getProviderId(), stats);
            }
            log.info("从数据库加载了 {} 条 Provider 运行态数据", list.size());
        } catch (Exception e) {
            log.error("加载 Provider 运行态数据失败", e);
        }
    }

    public ProviderRuntimeState get(String providerId) {
        return stateMap.computeIfAbsent(
                providerId,
                k -> new ProviderRuntimeState(providerId)
        );
    }

    public Collection<ProviderRuntimeState> all() {
        return stateMap.values();
    }

    public void clear() {
        stateMap.clear();
    }
}