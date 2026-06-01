package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("provider_endpoints")
public class ProviderEndpoint {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;

    private Integer protocolType;

    private String baseUrl;
}
