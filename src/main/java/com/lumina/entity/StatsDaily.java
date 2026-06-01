package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stats_daily")
public class StatsDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Long providerId;
    private String providerName;
    private String modelName;
    private Long totalRequests;
    private Long successCount;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private BigDecimal totalCost;
    private Long totalLatencyMs;
    private Long totalCacheReadTokens;
    private Long totalCacheCreationTokens;
    private Long cacheHitCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
