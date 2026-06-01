package com.lumina.dto;

import com.lumina.config.OverrideCircuitBreakerConfig;
import lombok.Data;

import java.util.List;

@Data
public class ModelGroupConfigItem {

    private Long providerId;

    private String providerName;

    private String modelName;

    private Integer weight;

    private String apiKey;

    private String baseUrl;

    private Integer providerType;

    /** 供应商支持的所有协议类型，逗号分隔，如 "0,2" */
    private String supportedTypes;

    /** 供应商所有端点 JSON，格式 {"0":"url1","2":"url2"} */
    private String endpointsJson;

    /**
     * Provider 级别熔断器配置覆盖
     * 优先级高于 Group 级别配置
     */
    private OverrideCircuitBreakerConfig circuitBreakerConfig;
}
