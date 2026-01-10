package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.RequestLog;
import com.lumina.service.RequestLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/request-logs")
public class RequestLogController {

    @Autowired
    private RequestLogService requestLogService;

    @GetMapping
    public ApiResponse<List<RequestLog>> getAllRequestLogs() {
        List<RequestLog> logs = requestLogService.list();
        return ApiResponse.success(logs);
    }

    @GetMapping("/{id}")
    public ApiResponse<RequestLog> getRequestLogById(@PathVariable Long id) {
        RequestLog log = requestLogService.getById(id);
        if (log == null) {
            throw new IllegalArgumentException("RequestLog not found with id: " + id);
        }
        return ApiResponse.success(log);
    }

    @PostMapping
    public ApiResponse<RequestLog> createRequestLog(@RequestBody RequestLog log) {
        log.setCreatedAt(LocalDateTime.now());
        boolean success = requestLogService.save(log);
        if (!success) {
            throw new IllegalArgumentException("Failed to create request log");
        }
        return ApiResponse.success(log);
    }

    @GetMapping("/page")
    public ApiResponse<Page<RequestLog>> getRequestLogsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<RequestLog> page = requestLogService.page(new Page<>(current, size));
        return ApiResponse.success(page);
    }

    @GetMapping("/channel/{channelId}")
    public ApiResponse<List<RequestLog>> getLogsByChannelId(@PathVariable Long channelId) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId);
        wrapper.orderByDesc("request_time");
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ApiResponse.success(logs);
    }

    @GetMapping("/model/{modelName}")
    public ApiResponse<List<RequestLog>> getLogsByModelName(@PathVariable String modelName) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.eq("request_model_name", modelName);
        wrapper.orderByDesc("request_time");
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ApiResponse.success(logs);
    }

    @GetMapping("/date-range")
    public ApiResponse<List<RequestLog>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.between("request_time", startTime, endTime);
        wrapper.orderByDesc("request_time");
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ApiResponse.success(logs);
    }

    @GetMapping("/recent")
    public ApiResponse<List<RequestLog>> getRecentLogs(@RequestParam(defaultValue = "100") Integer limit) {
        QueryWrapper<RequestLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("request_time");
        wrapper.last("LIMIT " + limit);
        List<RequestLog> logs = requestLogService.list(wrapper);
        return ApiResponse.success(logs);
    }
}
