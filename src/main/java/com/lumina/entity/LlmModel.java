package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("llm_models")
public class LlmModel {
    @TableId(type = IdType.INPUT)
    private String modelName;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private BigDecimal cacheReadPrice;
    private BigDecimal cacheWritePrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
