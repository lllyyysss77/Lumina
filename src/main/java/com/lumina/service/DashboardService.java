package com.lumina.service;

import com.lumina.dto.DashboardOverviewDto;
import com.lumina.dto.ModelTokenUsageDto;
import com.lumina.dto.ProviderStatsDto;
import com.lumina.dto.RequestTrafficDto;
import com.lumina.mapper.DashboardMapper;
import com.lumina.mapper.ProviderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;


    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取仪表盘概览统计
     * 总请求数、预估总费用、平均延迟、成功率统计全部数据
     * 增长率/变化率基于当天对比前一天的数据
     */
    public DashboardOverviewDto getOverview() {
        // 获取全部统计数据
        DashboardOverviewDto allStats = dashboardMapper.getAllOverviewStats();

        // 计算当天和前一天的日期范围
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        // 获取当天的统计数据（用于计算增长率）
        DashboardOverviewDto todayStats = dashboardMapper.getDateRangeStats(
                todayStart.format(FORMATTER),
                now.format(FORMATTER)
        );

        // 获取前一天的统计数据（用于计算增长率）
        DashboardOverviewDto yesterdayStats = dashboardMapper.getDateRangeStats(
                yesterdayStart.format(FORMATTER),
                todayStart.format(FORMATTER)
        );

        if (allStats != null) {
            // 计算增长率/变化率（当天 vs 前一天）
            if (yesterdayStats != null && yesterdayStats.getTotalRequests() > 0) {
                allStats.setRequestGrowthRate(
                        ((todayStats.getTotalRequests() - yesterdayStats.getTotalRequests()) * 100.0) / yesterdayStats.getTotalRequests()
                );
            } else {
                allStats.setRequestGrowthRate(todayStats != null && todayStats.getTotalRequests() > 0 ? 100.0 : 0.0);
            }

            if (yesterdayStats != null && yesterdayStats.getTotalCost().doubleValue() > 0) {
                allStats.setCostGrowthRate(
                        ((todayStats.getTotalCost().doubleValue() - yesterdayStats.getTotalCost().doubleValue()) * 100.0) / yesterdayStats.getTotalCost().doubleValue()
                );
            } else {
                allStats.setCostGrowthRate(todayStats != null && todayStats.getTotalCost().doubleValue() > 0 ? 100.0 : 0.0);
            }

            if (yesterdayStats != null) {
                allStats.setLatencyChange(todayStats.getAvgLatency() - yesterdayStats.getAvgLatency());
                allStats.setSuccessRateChange(todayStats.getSuccessRate() - yesterdayStats.getSuccessRate());
            } else {
                allStats.setLatencyChange(0.0);
                allStats.setSuccessRateChange(0.0);
            }
        }

        return allStats;
    }

    /**
     * 获取24小时请求流量
     */
    public List<RequestTrafficDto> getRequestTraffic() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        return dashboardMapper.getRequestTraffic(last24Hours.format(FORMATTER));
    }

    /**
     * 获取模型 Token 使用统计
     */
    public List<ModelTokenUsageDto> getModelTokenUsage() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        List<ModelTokenUsageDto> usageList = dashboardMapper.getModelTokenUsage(last24Hours.format(FORMATTER));

        long totalTokens = usageList.stream()
                .mapToLong(ModelTokenUsageDto::getTotalTokens)
                .sum();

        usageList.forEach(usage -> {
            if (totalTokens > 0) {
                usage.setPercentage((usage.getTotalTokens() * 100.0) / totalTokens);
            } else {
                usage.setPercentage(0.0);
            }
        });

        return usageList;
    }

    /**
     * 获取供应商统计排名
     */
    public List<ProviderStatsDto> getProviderStats(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }

        List<ProviderStatsDto> statsList = dashboardMapper.getProviderStats(limit);

        for (int i = 0; i < statsList.size(); i++) {
            ProviderStatsDto stats = statsList.get(i);
            stats.setRank(i + 1);
        }

        return statsList;
    }
}
