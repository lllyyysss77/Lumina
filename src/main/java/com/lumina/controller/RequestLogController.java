package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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


    @GetMapping("/page")
    public ApiResponse<Page<RequestLog>> getRequestLogsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<RequestLog> page = requestLogService.page(new Page<>(current, size),
                new LambdaQueryWrapper<RequestLog>()
                        .select(RequestLog::getId, RequestLog::getRequestTime,RequestLog::getStatus,
                                RequestLog::getRequestModelName, RequestLog::getActualModelName, RequestLog::getFirstTokenMs,
                                RequestLog::getInputTokens,RequestLog::getOutputTokens,RequestLog::getRetryCount)
                        .orderByDesc(RequestLog::getRequestTime));
        return ApiResponse.success(page);
    }



    @GetMapping("/{id}")
    public ApiResponse<RequestLog> getRequestLogById(@PathVariable Long id) {
        RequestLog log = requestLogService.getById(id);
        if (log == null) {
            throw new IllegalArgumentException("RequestLog not found with id: " + id);
        }
        return ApiResponse.success(log);
    }


}
