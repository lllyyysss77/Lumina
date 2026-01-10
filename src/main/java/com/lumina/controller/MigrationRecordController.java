package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.MigrationRecord;
import com.lumina.service.MigrationRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/migration")
public class MigrationRecordController {

    @Autowired
    private MigrationRecordService migrationRecordService;

    @GetMapping
    public ApiResponse<List<MigrationRecord>> getAllMigrationRecords() {
        QueryWrapper<MigrationRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("version");
        List<MigrationRecord> records = migrationRecordService.list(wrapper);
        return ApiResponse.success(records);
    }

    @GetMapping("/{version}")
    public ApiResponse<MigrationRecord> getMigrationRecordByVersion(@PathVariable Integer version) {
        MigrationRecord record = migrationRecordService.getById(version);
        if (record == null) {
            throw new IllegalArgumentException("MigrationRecord not found with version: " + version);
        }
        return ApiResponse.success(record);
    }

    @PostMapping
    public ApiResponse<MigrationRecord> createMigrationRecord(@RequestBody MigrationRecord record) {
        record.setExecutedAt(LocalDateTime.now());
        boolean success = migrationRecordService.save(record);
        if (!success) {
            throw new IllegalArgumentException("Failed to create migration record");
        }
        return ApiResponse.success(record);
    }

    @GetMapping("/latest")
    public ApiResponse<MigrationRecord> getLatestMigrationRecord() {
        QueryWrapper<MigrationRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("version");
        wrapper.last("LIMIT 1");
        MigrationRecord record = migrationRecordService.getOne(wrapper);
        if (record == null) {
            throw new IllegalArgumentException("No migration records found");
        }
        return ApiResponse.success(record);
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<MigrationRecord>> getMigrationRecordsByStatus(@PathVariable Integer status) {
        QueryWrapper<MigrationRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);
        wrapper.orderByDesc("version");
        List<MigrationRecord> records = migrationRecordService.list(wrapper);
        return ApiResponse.success(records);
    }
}
