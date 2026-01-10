package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("groups")
public class Group {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer balanceMode;
    private String matchRegex;
    private Integer firstTokenTimeout;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
