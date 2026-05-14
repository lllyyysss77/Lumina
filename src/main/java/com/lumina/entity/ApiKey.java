package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("api_keys")
public class ApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String apiKey;
    private Boolean isEnabled;
    private Long expiredAt;
    private BigDecimal maxAmount;
    private String supportedModels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
