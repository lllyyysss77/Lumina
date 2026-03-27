package com.lumina.state;

import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.entity.Group;
import com.lumina.entity.ProviderRuntimeStats;
import com.lumina.mapper.ProviderRuntimeStatsMapper;
import com.lumina.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderStatsPersistenceJob {

    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final ProviderStateRegistry stateRegistry;
    private final ProviderRuntimeStatsMapper mapper;
    private final GroupService groupService;
    private volatile long lastCleanupAt;

    @Scheduled(fixedDelay = 10_000) // 每 10 秒落盘一次
    public void flush() {
        int persistCount = 0;
        int removedCount = 0;
        List<ProviderRuntimeStats> dirtyRows = new ArrayList<>();

        for (ProviderRuntimeState stats : stateRegistry.all()) {
            if (!stats.isDirty()) {
                continue;
            }

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
                row.setConsecutiveFailures(stats.getConsecutiveFailures().get());
                row.setOpenAttempt(stats.getOpenAttempt());
                row.setNextProbeAt(stats.getNextProbeAt());
                row.setUpdatedAt(LocalDateTime.now());
                dirtyRows.add(row);
            } catch (Exception e) {
                log.error("持久化 Provider 状态失败: {}", stats.getProviderId(), e);
            }
        }

        if (!dirtyRows.isEmpty()) {
            try {
                mapper.upsertBatch(dirtyRows);
                persistCount = dirtyRows.size();
                Set<String> persistedIds = new HashSet<>();
                for (ProviderRuntimeStats row : dirtyRows) {
                    persistedIds.add(row.getProviderId());
                }
                for (ProviderRuntimeState stats : stateRegistry.all()) {
                    if (persistedIds.contains(stats.getProviderId())) {
                        stats.clearDirty();
                    }
                }
            } catch (Exception e) {
                log.error("批量持久化 Provider 状态失败", e);
            }
        }

        if (persistCount > 0) {
            log.debug("已同步 {} 条 Provider 运行态数据到数据库", persistCount);
        }

        if (shouldRunCleanup()) {
            removedCount = cleanupStaleProviders();
            lastCleanupAt = System.currentTimeMillis();
        }
        if (removedCount > 0) {
            log.info("已清理 {} 个过期的 Provider 运行态数据", removedCount);
        }
    }

    /**
     * 获取所有有效的 Provider IDs
     * 从所有 ModelGroupConfig 中提取并生成 Provider IDs
     * @return 有效的 Provider ID 集合
     */
    private Set<String> getValidProviderIds() {
        Set<String> validIds = new HashSet<>();

        try {
            // 获取所有分组
            List<Group> allGroups = groupService.list();

            // 遍历每个分组，获取其配置并生成 Provider IDs
            for (Group group : allGroups) {
                try {
                    ModelGroupConfig config = groupService.getModelGroupConfig(group.getName());
                    if (config != null && config.getItems() != null) {
                        for (ModelGroupConfigItem item : config.getItems()) {
                            String providerId = generateProviderId(item);
                            validIds.add(providerId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取分组配置失败: {}", group.getName(), e);
                }
            }

            log.debug("从 {} 个分组中获取到 {} 个有效的 Provider IDs", allGroups.size(), validIds.size());
        } catch (Exception e) {
            log.error("获取有效 Provider IDs 失败", e);
        }

        return validIds;
    }

    /**
     * 生成 Provider ID
     * 与 FailoverService 中的逻辑保持一致
     * @param item ModelGroupConfigItem
     * @return Provider ID
     */
    private String generateProviderId(ModelGroupConfigItem item) {
        return String.format("%s_%s_%s",
                item.getBaseUrl(),
                item.getApiKey() != null ? item.getApiKey().hashCode() : "null",
                item.getModelName());
    }

    private boolean shouldRunCleanup() {
        return System.currentTimeMillis() - lastCleanupAt >= CLEANUP_INTERVAL_MS;
    }

    private int cleanupStaleProviders() {
        Set<String> validProviderIds = getValidProviderIds();
        Set<String> staleProviderIds = new HashSet<>();
        for (ProviderRuntimeState stats : stateRegistry.all()) {
            if (!validProviderIds.contains(stats.getProviderId())) {
                staleProviderIds.add(stats.getProviderId());
            }
        }
        if (staleProviderIds.isEmpty()) {
            return 0;
        }

        stateRegistry.removeAll(staleProviderIds);
        int removed = mapper.deleteNotInProviderIds(validProviderIds);
        log.debug("清理过期 Provider 运行态数据: {}", staleProviderIds);
        return removed;
    }
}
