package com.lumina.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CircuitBreakerRecentEventDto {

    private String action;
    private String providerId;
    private String providerName;
    private String modelName;
    private String fromState;
    private String toState;
    private String reason;
    private String operator;
    private String timestamp;
}
