package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.RequestLog;
import com.lumina.service.RequestLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/request-logs")
public class RequestLogController {

    @Autowired
    private RequestLogService requestLogService;

    @GetMapping
    public ResponseEntity<List<RequestLog>> getAllRequestLogs() {
        List<RequestLog> logs = requestLogService.list();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RequestLog> getRequestLogById(@PathVariable Long id) {
        RequestLog log = requestLogService.getById(id);
        if (log == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(log);
    }

    @PostMapping
    public ResponseEntity<RequestLog> createRequestLog(@RequestBody RequestLog log) {
        log.setCreatedAt(LocalDateTime.now());
        boolean success = requestLogService.save(log);
        return success ? ResponseEntity.ok(log) : ResponseEntity.badRequest().build();
    }

    @GetMapping("/page")
    public ResponseEntity<Page<RequestLog>> getRequestLogsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<RequestLog> page = requestLogService.page(new Page<>(current, size));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<RequestLog>> getLogsByChannelId(@PathVariable Long channelId) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId);
        wrapper.orderByDesc("request_time");
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/model/{modelName}")
    public ResponseEntity<List<RequestLog>> getLogsByModelName(@PathVariable String modelName) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.eq("request_model_name", modelName);
        wrapper.orderByDesc("request_time");
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<RequestLog>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.between("request_time", startTime, endTime);
        wrapper.orderByDesc("request_time");
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<RequestLog>> getRecentLogs(@RequestParam(defaultValue = "100") Integer limit) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("request_time");
        wrapper.last("LIMIT " + limit);
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ResponseEntity.ok(logs);
    }
}
