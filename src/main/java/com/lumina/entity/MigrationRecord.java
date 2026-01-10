package com.lumina.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("migration_records")
public class MigrationRecord {
    @TableId(type = IdType.INPUT)
    private Integer version;
    private Integer status;
    private LocalDateTime executedAt;
}
