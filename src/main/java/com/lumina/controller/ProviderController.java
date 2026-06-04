package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.Provider;
import com.lumina.entity.ProviderEndpoint;
import com.lumina.mapper.ProviderEndpointMapper;
import com.lumina.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private ProviderEndpointMapper endpointMapper;

    @GetMapping
    public ApiResponse<List<Provider>> getAllProviders() {
        List<Provider> providers = providerService.list();
        providers.forEach(this::fillEndpoints);
        return ApiResponse.success(providers);
    }

    @GetMapping("/{id}")
    public ApiResponse<Provider> getProviderById(@PathVariable Long id) {
        Provider provider = providerService.getById(id);
        if (provider == null) {
            throw new IllegalArgumentException("供应商不存在");
        }
        fillEndpoints(provider);
        return ApiResponse.success(provider);
    }

    @PostMapping
    public ApiResponse<Provider> createProvider(@RequestBody @Valid Provider provider) {
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        if (provider.getType() == null) {
            provider.setType("");
        }
        prepareProviderForSave(provider);
        providerService.save(provider);
        saveEndpoints(provider);
        return ApiResponse.success("供应商创建成功", provider);
    }

    @PutMapping("/{id}")
    public ApiResponse<Provider> updateProvider(@PathVariable Long id, @RequestBody Provider provider) {
        Provider existing = providerService.getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("供应商不存在");
        }
        provider.setId(id);
        provider.setUpdatedAt(LocalDateTime.now());
        if (!StringUtils.hasText(provider.getApiKey())) {
            provider.setApiKey(existing.getApiKey());
        }
        prepareProviderForSave(provider);
        providerService.updateById(provider);
        saveEndpoints(provider);
        fillEndpoints(provider);
        return ApiResponse.success("供应商更新成功", provider);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        // endpoints cascade via FK
        providerService.removeById(id);
        return ApiResponse.success("供应商删除成功", null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<Provider>> getProvidersByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isEnabled,
            @RequestParam(required = false) String modelName) {
        LambdaQueryWrapper<Provider> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Provider::getName, name);
        wrapper.like(StringUtils.hasText(type), Provider::getType, type);
        wrapper.eq(isEnabled != null, Provider::getIsEnabled, isEnabled);
        wrapper.like(StringUtils.hasText(modelName), Provider::getModelName, modelName);
        wrapper.orderByDesc(Provider::getCreatedAt);
        Page<Provider> page = providerService.page(new Page<>(current, size), wrapper);
        page.getRecords().forEach(this::fillEndpoints);
        return ApiResponse.success(page);
    }

    @GetMapping("/enabled")
    public ApiResponse<List<Provider>> getEnabledProviders() {
        QueryWrapper<Provider> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        return ApiResponse.success(providerService.list(wrapper));
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<Provider>> getProvidersByType(@PathVariable String type) {
        QueryWrapper<Provider> wrapper = new QueryWrapper<>();
        wrapper.like("type", type);
        return ApiResponse.success(providerService.list(wrapper));
    }


    @PostMapping("/models")
    public ApiResponse<List<String>> getModels(@RequestBody Provider provider) {
        if (!StringUtils.hasText(provider.getApiKey()) && provider.getId() != null) {
            Provider existing = providerService.getById(provider.getId());
            if (existing != null) {
                provider.setApiKey(existing.getApiKey());
            }
        }
        return ApiResponse.success(providerService.getModels(provider));
    }

    private void fillEndpoints(Provider provider) {
        List<ProviderEndpoint> endpoints = endpointMapper.selectList(
                new LambdaQueryWrapper<ProviderEndpoint>()
                        .eq(ProviderEndpoint::getProviderId, provider.getId()));
        provider.setEndpoints(endpoints);
    }

    private void saveEndpoints(Provider provider) {
        // Delete existing endpoints and re-insert
        endpointMapper.delete(new LambdaQueryWrapper<ProviderEndpoint>()
                .eq(ProviderEndpoint::getProviderId, provider.getId()));
        if (provider.getEndpoints() != null) {
            for (ProviderEndpoint ep : provider.getEndpoints()) {
                ep.setProviderId(provider.getId());
                endpointMapper.insert(ep);
            }
        }
    }

    private void prepareProviderForSave(Provider provider) {
        validateEndpoints(provider);
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            provider.setBaseUrl(resolveDefaultBaseUrl(provider));
        }
    }

    private void validateEndpoints(Provider provider) {
        List<ProviderEndpoint> endpoints = provider.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            if (!StringUtils.hasText(provider.getBaseUrl())) {
                throw new IllegalArgumentException("API 基础地址不能为空");
            }
            return;
        }

        for (ProviderEndpoint endpoint : endpoints) {
            if (endpoint.getProtocolType() == null) {
                throw new IllegalArgumentException("协议类型不能为空");
            }
            if (!StringUtils.hasText(endpoint.getBaseUrl())) {
                throw new IllegalArgumentException("API 基础地址不能为空");
            }
        }
    }

    private String resolveDefaultBaseUrl(Provider provider) {
        List<ProviderEndpoint> endpoints = provider.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("API 基础地址不能为空");
        }

        if (StringUtils.hasText(provider.getType())) {
            String primaryType = provider.getType().split(",")[0].trim();
            for (ProviderEndpoint endpoint : endpoints) {
                if (primaryType.equals(String.valueOf(endpoint.getProtocolType()))) {
                    return endpoint.getBaseUrl();
                }
            }
        }

        return endpoints.get(0).getBaseUrl();
    }
}
