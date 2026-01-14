package com.lumina.state;

import com.lumina.entity.ProviderRuntimeStats;
import com.lumina.mapper.ProviderRuntimeStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderStatsPersistenceJob {

    private final ProviderStateRegistry stateRegistry;
    private final ProviderRuntimeStatsMapper mapper;

    @Scheduled(fixedDelay = 10_000) // 每 10 秒落盘一次
    public void flush() {
        int count = 0;
        for (ProviderRuntimeState stats : stateRegistry.all()) {
            try {
                ProviderRuntimeStats row = new ProviderRuntimeStats();
                row.setProviderId(stats.getProviderId());
                row.setProviderName(stats.getProviderName());

                row.setSuccessRateEma(stats.getSuccessRateEma());
                row.setLatencyEmaMs(stats.getLatencyEmaMs());
                row.setScore(stats.getScore());

                row.setTotalRequests(stats.getTotalRequests().get());
                row.setSuccessRequests(stats.getSuccessRequests().get());
                row.setFailureRequests(stats.getFailureRequests().get());

                row.setCircuitState(stats.getCircuitState().name());
                row.setCircuitOpenedAt(stats.getCircuitOpenedAt());

                row.setUpdatedAt(LocalDateTime.now());

                mapper.upsert(row);
                count++;
            } catch (Exception e) {
                log.error("持久化 Provider 状态失败: {}", stats.getProviderId(), e);
            }
        }
        if (count > 0) {
            log.debug("已同步 {} 条 Provider 运行态数据到数据库", count);
        }
    }
}
