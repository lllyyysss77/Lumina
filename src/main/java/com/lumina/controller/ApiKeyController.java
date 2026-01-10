package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.ApiKey;
import com.lumina.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    @GetMapping
    public ApiResponse<List<ApiKey>> getAllApiKeys() {
        List<ApiKey> apiKeys = apiKeyService.list();
        return ApiResponse.success(apiKeys);
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiKey> getApiKeyById(@PathVariable Long id) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            throw new IllegalArgumentException("ApiKey not found with id: " + id);
        }
        return ApiResponse.success(apiKey);
    }

    @PostMapping
    public ApiResponse<ApiKey> createApiKey(@RequestBody ApiKey apiKey) {
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());
        boolean success = apiKeyService.save(apiKey);
        if (!success) {
            throw new IllegalArgumentException("Failed to create api key");
        }
        return ApiResponse.success(apiKey);
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiKey> updateApiKey(@PathVariable Long id, @RequestBody ApiKey apiKey) {
        apiKey.setId(id);
        apiKey.setUpdatedAt(LocalDateTime.now());
        boolean success = apiKeyService.updateById(apiKey);
        if (!success) {
            throw new IllegalArgumentException("ApiKey not found with id: " + id);
        }
        return ApiResponse.success(apiKey);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteApiKey(@PathVariable Long id) {
        boolean success = apiKeyService.removeById(id);
        if (!success) {
            throw new IllegalArgumentException("ApiKey not found with id: " + id);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<ApiKey>> getApiKeysByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<ApiKey> page = apiKeyService.page(new Page<>(current, size));
        return ApiResponse.success(page);
    }

    @GetMapping("/enabled")
    public ApiResponse<List<ApiKey>> getEnabledApiKeys() {
        QueryWrapper<ApiKey> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        List<ApiKey> apiKeys = apiKeyService.list(wrapper);
        return ApiResponse.success(apiKeys);
    }

    @GetMapping("/key/{apiKey}")
    public ApiResponse<ApiKey> getApiKeyByValue(@PathVariable String apiKey) {
        QueryWrapper<ApiKey> wrapper = new QueryWrapper<>();
        wrapper.eq("api_key", apiKey);
        ApiKey key = apiKeyService.getOne(wrapper);
        if (key == null) {
            throw new IllegalArgumentException("ApiKey not found with key: " + apiKey);
        }
        return ApiResponse.success(key);
    }
}
