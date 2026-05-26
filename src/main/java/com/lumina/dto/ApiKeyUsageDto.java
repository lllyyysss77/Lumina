package com.lumina.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ApiKeyUsageDto {
    private Long id;
    private String name;
    private String apiKey;
    private Boolean isEnabled;
    private Long expiredAt;
    private BigDecimal maxAmount;
    private Long totalRequests;
    private Long successRequests;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private BigDecimal totalCost;
}
