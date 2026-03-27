package com.lumina.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 仪表盘概览统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDto {
    /**
     * 总请求数
     */
    private Long totalRequests;

    /**
     * 总请求数增长率（百分比）
     */
    private Double requestGrowthRate;

    /**
     * 总 Token 数
     */
    private Long totalTokens;

    /**
     * 总 Token 数增长率（百分比）
     */
    private Double tokenGrowthRate;

    /**
     * 预估总费用
     */
    private BigDecimal totalCost;

    /**
     * 费用增长率（百分比）
     */
    private Double costGrowthRate;

    /**
     * 平均延迟（毫秒）
     */
    private Double avgLatency;

    /**
     * 延迟变化（毫秒，负数表示减少）
     */
    private Double latencyChange;

    /**
     * 成功率（百分比）
     */
    private Double successRate;

    /**
     * 成功率变化（百分比）
     */
    private Double successRateChange;
}
