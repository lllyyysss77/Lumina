package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumina.entity.MigrationRecord;
import com.lumina.service.MigrationRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/migration")
public class MigrationRecordController {

    @Autowired
    private MigrationRecordService migrationRecordService;

    @GetMapping
    public ResponseEntity<List<MigrationRecord>> getAllMigrationRecords() {
        QueryWrapper<MigrationRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("version");
        List<MigrationRecord> records = migrationRecordService.list(wrapper);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/{version}")
    public ResponseEntity<MigrationRecord> getMigrationRecordByVersion(@PathVariable Integer version) {
        MigrationRecord record = migrationRecordService.getById(version);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }

    @PostMapping
    public ResponseEntity<MigrationRecord> createMigrationRecord(@RequestBody MigrationRecord record) {
        record.setExecutedAt(LocalDateTime.now());
        boolean success = migrationRecordService.save(record);
        return success ? ResponseEntity.ok(record) : ResponseEntity.badRequest().build();
    }

    @GetMapping("/latest")
    public ResponseEntity<MigrationRecord> getLatestMigrationRecord() {
        QueryWrapper<MigrationRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("version");
        wrapper.last("LIMIT 1");
        MigrationRecord record = migrationRecordService.getOne(wrapper);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<MigrationRecord>> getMigrationRecordsByStatus(@PathVariable Integer status) {
        QueryWrapper<MigrationRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status);
        wrapper.orderByDesc("version");
        List<MigrationRecord> records = migrationRecordService.list(wrapper);
        return ResponseEntity.ok(records);
    }
}
