package com.lumina.controller;

import com.lumina.dto.CircuitBreakerControlRequest;
import com.lumina.dto.CircuitBreakerRecentEventDto;
import com.lumina.dto.CircuitBreakerStatusResponse;
import com.lumina.service.CircuitBreakerManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 熔断器管控接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerController {

    private final CircuitBreakerManagementService managementService;

    /**
     * 获取单个 Provider 的熔断器状态
     */
    @GetMapping("/status/{providerId}")
    public ResponseEntity<CircuitBreakerStatusResponse> getStatus(@PathVariable String providerId) {
        CircuitBreakerStatusResponse status = managementService.getStatus(providerId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * 获取所有 Provider 的熔断器状态列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<CircuitBreakerStatusResponse>> listAll() {
        return ResponseEntity.ok(managementService.listAllStatus());
    }

    @GetMapping("/management/list")
    public ResponseEntity<List<CircuitBreakerStatusResponse>> listManagementStatus() {
        return ResponseEntity.ok(managementService.listManagementStatus());
    }

    @GetMapping("/management/recent-events")
    public ResponseEntity<List<CircuitBreakerRecentEventDto>> listRecentEvents(
            @RequestParam(defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(managementService.listRecentManualEvents(limit));
    }

    /**
     * 手动控制熔断器状态
     *
     * @param request 控制请求
     * @param operator 操作人（可选，通过 Header 传递）
     */
    @PostMapping("/control")
    public ResponseEntity<CircuitBreakerStatusResponse> control(
            @Valid @RequestBody CircuitBreakerControlRequest request,
            @RequestHeader(value = "X-Operator", required = false) String operator) {

        log.info("收到手动控制请求: providerId={}, targetState={}, operator={}",
                request.getProviderId(), request.getTargetState(), operator);

        CircuitBreakerStatusResponse response = managementService.controlCircuitBreaker(request, operator);
        return ResponseEntity.ok(response);
    }

    /**
     * 释放手动控制，恢复自动管理
     */
    @PostMapping("/release/{providerId}")
    public ResponseEntity<CircuitBreakerStatusResponse> releaseManualControl(
            @PathVariable String providerId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        log.info("释放手动控制请求: providerId={}", providerId);

        CircuitBreakerStatusResponse response = managementService.releaseManualControl(providerId, operator);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 批量获取指定 Provider 的状态
     */
    @PostMapping("/status/batch")
    public ResponseEntity<List<CircuitBreakerStatusResponse>> batchGetStatus(@RequestBody List<String> providerIds) {
        List<CircuitBreakerStatusResponse> responses = providerIds.stream()
                .map(managementService::getStatus)
                .filter(status -> status != null)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
