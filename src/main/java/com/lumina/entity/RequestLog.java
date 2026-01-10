package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("request_logs")
public class RequestLog {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long requestTime;
    private String requestModelName;
    private Long channelId;
    private String channelName;
    private String actualModelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer firstTokenTime;
    private Integer totalTime;
    private BigDecimal cost;
    private String requestContent;
    private String responseContent;
    private String errorMessage;
    private LocalDateTime createdAt;
}
