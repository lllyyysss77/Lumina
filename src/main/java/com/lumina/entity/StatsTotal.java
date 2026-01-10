package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stats_total")
public class StatsTotal {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Long inputTokens;
    private Long outputTokens;
    private BigDecimal inputCost;
    private BigDecimal outputCost;
    private Long waitTime;
    private Long requestSuccessCount;
    private Long requestFailedCount;
    private LocalDateTime updatedAt;
}
