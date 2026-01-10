package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.ApiKey;
import com.lumina.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    @GetMapping
    public ResponseEntity<List<ApiKey>> getAllApiKeys() {
        List<ApiKey> apiKeys = apiKeyService.list();
        return ResponseEntity.ok(apiKeys);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiKey> getApiKeyById(@PathVariable Long id) {
        ApiKey apiKey = apiKeyService.getById(id);
        if (apiKey == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(apiKey);
    }

    @PostMapping
    public ResponseEntity<ApiKey> createApiKey(@RequestBody ApiKey apiKey) {
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());
        boolean success = apiKeyService.save(apiKey);
        return success ? ResponseEntity.ok(apiKey) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiKey> updateApiKey(@PathVariable Long id, @RequestBody ApiKey apiKey) {
        apiKey.setId(id);
        apiKey.setUpdatedAt(LocalDateTime.now());
        boolean success = apiKeyService.updateById(apiKey);
        return success ? ResponseEntity.ok(apiKey) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiKey(@PathVariable Long id) {
        boolean success = apiKeyService.removeById(id);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/page")
    public ResponseEntity<Page<ApiKey>> getApiKeysByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<ApiKey> page = apiKeyService.page(new Page<>(current, size));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<ApiKey>> getEnabledApiKeys() {
        QueryWrapper<ApiKey> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        List<ApiKey> apiKeys = apiKeyService.list(wrapper);
        return ResponseEntity.ok(apiKeys);
    }

    @GetMapping("/key/{apiKey}")
    public ResponseEntity<ApiKey> getApiKeyByValue(@PathVariable String apiKey) {
        QueryWrapper<ApiKey> wrapper = new QueryWrapper<>();
        wrapper.eq("api_key", apiKey);
        ApiKey key = apiKeyService.getOne(wrapper);
        return key != null ? ResponseEntity.ok(key) : ResponseEntity.notFound().build();
    }
}
