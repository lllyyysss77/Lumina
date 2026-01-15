package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("llm_models")
public class LlmModel {
    @TableId(type = IdType.INPUT)
    private String modelName;
    private String provider;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private Integer contextLimit;
    private Integer outputLimit;
    private BigDecimal cacheReadPrice;
    private BigDecimal cacheWritePrice;
    private Boolean isReasoning;
    private Boolean isToolCall;
    private String inputType;
    private String lastUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
