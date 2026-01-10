package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("group_items")
public class GroupItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long channelId;
    private String modelName;
    private Integer priority;
    private Integer weight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
