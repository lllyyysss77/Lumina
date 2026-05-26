package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.dto.ApiKeyUsageDto;
import com.lumina.entity.ApiKey;
import com.lumina.mapper.ApiKeyMapper;
import com.lumina.service.ApiKeyService;
import com.lumina.service.HotPathCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.Serializable;
import java.math.BigDecimal;
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
            ApiKey key = this.getOne(queryWrapper);
            if (key == null) {
                return false;
            }
            if (key.getExpiredAt() != null && key.getExpiredAt() > 0) {
                return System.currentTimeMillis() / 1000 < key.getExpiredAt();
            }
            return true;
        })).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> hasAvailableQuota(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> {
            ApiKeyUsageDto usage = baseMapper.selectApiKeyUsageByKey(apiKey);
            if (usage == null) {
                return false;
            }

            BigDecimal maxAmount = usage.getMaxAmount();
            if (maxAmount == null) {
                return true;
            }

            BigDecimal totalCost = usage.getTotalCost() != null ? usage.getTotalCost() : BigDecimal.ZERO;
            return totalCost.compareTo(maxAmount) < 0;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public ApiKey updateMaxAmount(Long id, BigDecimal maxAmount) {
        if (maxAmount != null && maxAmount.signum() < 0) {
            throw new IllegalArgumentException("Max amount must be greater than or equal to 0");
        }

        ApiKey existing = this.getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("ApiKey not found with id: " + id);
        }

        LambdaUpdateWrapper<ApiKey> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ApiKey::getId, id)
                .set(ApiKey::getMaxAmount, maxAmount)
                .set(ApiKey::getUpdatedAt, LocalDateTime.now());

        boolean updated = super.update(wrapper);
        if (!updated) {
            throw new IllegalArgumentException("Failed to update api key quota");
        }
        hotPathCacheService.invalidateAllApiKeys();
        return this.getById(id);
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
