package com.lumina.logging;

import lombok.Data;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class RequestLogContext {
    private String id;
    private String requestId;

    private Long startNano;
    private Long requestTime;

    private String requestType;

    private String requestModel;
    private String actualModel;

    private Long providerId;
    private String providerName;

    private Boolean stream;

    private Integer inputTokens;
    private Integer outputTokens;

    private Integer firstTokenTime;
    private Integer firstTokenMs;
    private Integer totalTime;
    private Integer totalTimeMs;

    private BigDecimal cost = BigDecimal.ZERO;

    private String status = "SUCCESS";
    private String errorStage;
    private String errorMessage;

    private Integer retryCount = 0;

    private String requestContent;
    private String responseContent;

    private AtomicBoolean firstTokenArrived = new AtomicBoolean(false);
    private StringBuilder responseBuffer = new StringBuilder();
}