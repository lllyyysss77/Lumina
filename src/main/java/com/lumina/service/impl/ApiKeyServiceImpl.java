package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.ApiKey;
import com.lumina.mapper.ApiKeyMapper;
import com.lumina.service.ApiKeyService;
import com.lumina.service.HotPathCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    @Autowired
    private HotPathCacheService hotPathCacheService;

    @Override
    public ApiKey generateApiKey(String name) {
        ApiKey apiKey = new ApiKey();
        apiKey.setName(name);
        apiKey.setApiKey("sk-" + UUID.randomUUID().toString().replace("-", ""));
        apiKey.setIsEnabled(true);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());
        this.save(apiKey);
        hotPathCacheService.invalidateApiKey(apiKey.getApiKey());
        return apiKey;
    }

    @Override
    public Mono<Boolean> validateApiKey(String apiKey) {
        Boolean cached = hotPathCacheService.getCachedApiKeyValidity(apiKey);
        if (cached != null) {
            return Mono.just(cached);
        }

        return Mono.fromCallable(() -> hotPathCacheService.getApiKeyValidity(apiKey, () -> {
            LambdaQueryWrapper<ApiKey> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ApiKey::getApiKey, apiKey)
                    .eq(ApiKey::getIsEnabled, true);
            return this.count(queryWrapper) > 0;
        })).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public boolean save(ApiKey entity) {
        boolean saved = super.save(entity);
        if (saved) {
            hotPathCacheService.invalidateAllApiKeys();
        }
        return saved;
    }

    @Override
    public boolean updateById(ApiKey entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            hotPathCacheService.invalidateAllApiKeys();
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean removed = super.removeById(id);
        if (removed) {
            hotPathCacheService.invalidateAllApiKeys();
        }
        return removed;
    }
}
