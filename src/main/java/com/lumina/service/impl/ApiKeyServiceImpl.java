package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.ApiKey;
import com.lumina.mapper.ApiKeyMapper;
import com.lumina.service.ApiKeyService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    @Override
    public ApiKey generateApiKey(String name) {
        ApiKey apiKey = new ApiKey();
        apiKey.setName(name);
        apiKey.setApiKey("sk-" + UUID.randomUUID().toString().replace("-", ""));
        apiKey.setIsEnabled(true);
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());
        this.save(apiKey);
        return apiKey;
    }

    @Override
    public Mono<Boolean> validateApiKey(String apiKey) {
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<ApiKey> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ApiKey::getApiKey, apiKey)
                    .eq(ApiKey::getIsEnabled, true);
            return this.count(queryWrapper) > 0;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
