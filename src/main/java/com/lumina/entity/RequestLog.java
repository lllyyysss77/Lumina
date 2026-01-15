package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("request_logs")
public class RequestLog {
    @TableId(type = IdType.INPUT)
    private String id;
    private String requestId;
    private Long requestTime;
    private String requestType;
    private String requestModelName;
    private String actualModelName;
    private Long providerId;
    private String providerName;
    private Boolean isStream;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer firstTokenTime;
    private Integer firstTokenMs;
    private Integer totalTime;
    private Integer total_time_ms;
    private BigDecimal cost;
    private String status;
    private String errorStage;
    private String errorMessage;
    private Integer retryCount;
    private String requestContent;
    private String responseContent;
    private LocalDateTime createdAt;
}
