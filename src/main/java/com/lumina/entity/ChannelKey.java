package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("channel_keys")
public class ChannelKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long channelId;
    private Boolean isEnabled;
    private String apiKey;
    private Integer statusCode;
    private Long lastUsedAt;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
