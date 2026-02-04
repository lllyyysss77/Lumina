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
     * 返回完整的 0-23 小时数据，没有数据的小时显示为 0
     */
    public List<RequestTrafficDto> getRequestTraffic() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);

        // 从数据库获取有数据的小时
        List<RequestTrafficDto> dbResults = dashboardMapper.getRequestTraffic(last24Hours.format(FORMATTER));

        // 创建一个 Map 用于快速查找，key 为小时数
        java.util.Map<Integer, RequestTrafficDto> dataMap = new java.util.HashMap<>();
        for (RequestTrafficDto dto : dbResults) {
            dataMap.put(dto.getHour(), dto);
        }

        // 构建完整的 0-23 小时数据
        List<RequestTrafficDto> result = new java.util.ArrayList<>();
        LocalDateTime currentHour = last24Hours.withMinute(0).withSecond(0).withNano(0);

        for (int i = 0; i < 24; i++) {
            int hour = currentHour.getHour();
            long timestamp = currentHour.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

            RequestTrafficDto dto = dataMap.get(hour);
            if (dto != null) {
                // 有数据，使用数据库返回的数据
                result.add(dto);
            } else {
                // 没有数据，创建一个 requestCount 为 0 的数据点
                result.add(RequestTrafficDto.builder()
                        .hour(hour)
                        .requestCount(0L)
                        .timestamp(timestamp)
                        .build());
            }

            currentHour = currentHour.plusHours(1);
        }

        return result;
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
