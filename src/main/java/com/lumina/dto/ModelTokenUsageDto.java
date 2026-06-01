package com.lumina.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型 Token 使用统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTokenUsageDto {
    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 输入 Token 数
     */
    private Long inputTokens;

    /**
     * 输出 Token 数
     */
    private Long outputTokens;

    /**
     * 总 Token 数
     */
    private Long totalTokens;

    /**
     * 请求次数
     */
    private Long requestCount;

    /**
     * 占比（百分比）
     */
    private Double percentage;

    /**
     * 缓存读取 Token 数
     */
    private Long cacheReadTokens;

    /**
     * 缓存命中率（百分比）
     */
    private Double cacheHitRate;
}
