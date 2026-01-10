package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("channels")
public class Channel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer type;
    private Boolean isEnabled;
    private String baseUrlList;
    private String modelName;
    private String customModelName;
    private Boolean useProxy;
    private Boolean autoSync;
    private Integer autoGroupMode;
    private String customHeaders;
    private String paramOverride;
    private String proxyUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
