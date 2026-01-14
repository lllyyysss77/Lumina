package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("provider_runtime_stats")
public class ProviderRuntimeStats {

    @TableId
    private String providerId;
    private String providerName;

    private Double successRateEma;
    private Double latencyEmaMs;
    private Double score;

    private Integer totalRequests;
    private Integer successRequests;
    private Integer failureRequests;

    private String circuitState;
    private Long circuitOpenedAt;

    private LocalDateTime updatedAt;
}
