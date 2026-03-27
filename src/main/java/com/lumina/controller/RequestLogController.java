package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.dto.RequestLogDetailDto;
import com.lumina.dto.RequestLogPayloadDto;
import com.lumina.entity.RequestLog;
import com.lumina.service.RequestLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
                        .select(RequestLog::getId, RequestLog::getRequestTime,RequestLog::getStatus,RequestLog::getProviderName,
                                RequestLog::getRequestModelName, RequestLog::getActualModelName, RequestLog::getFirstTokenMs,
                                RequestLog::getInputTokens,RequestLog::getOutputTokens,RequestLog::getRetryCount,RequestLog::getCost)
                        .orderByDesc(RequestLog::getRequestTime));
        return ApiResponse.success(page);
    }



    @GetMapping("/{id}")
    public Mono<ApiResponse<RequestLogDetailDto>> getRequestLogById(@PathVariable String id) {
        return Mono.fromCallable(() -> ApiResponse.success(requestLogService.getDetailMetaById(id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}/payloads")
    public Mono<ApiResponse<RequestLogPayloadDto>> getRequestLogPayloads(@PathVariable String id) {
        return Mono.fromCallable(() -> ApiResponse.success(requestLogService.getPayloadsById(id)))
                .subscribeOn(Schedulers.boundedElastic());
    }


}
