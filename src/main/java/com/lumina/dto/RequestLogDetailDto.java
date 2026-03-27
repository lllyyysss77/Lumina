package com.lumina.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RequestLogDetailDto {
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
    private Integer totalTimeMs;
    private BigDecimal cost;
    private String status;
    private String errorStage;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
}
