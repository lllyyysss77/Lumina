package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("providers")
public class Provider {
    @TableId(type = IdType.AUTO)
    private Long id;

    @NotEmpty(message = "名称不能为空")
    private String name;

    private String type;

    @TableField(exist = false)
    private List<ProviderEndpoint> endpoints;

    @NotNull(message = "请选择是否启用")
    private Boolean isEnabled;

    private String baseUrl;

    @NotEmpty(message = "模型名称不能为空")
    private String modelName;

    private String actualModel;

    private Boolean beta;

    @NotEmpty(message = "API 密钥不能为空")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    @NotNull(message = "请选择是否自动同步")
    private Boolean autoSync;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
