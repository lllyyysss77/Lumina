package com.lumina.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApiKeyQuotaUpdateRequest {
    private BigDecimal maxAmount;
}
