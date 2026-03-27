package com.lumina.service;

import com.lumina.config.LuminaProperties;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.entity.LlmModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class HotPathCacheService {

    private final ConcurrentHashMap<String, CacheEntry<ModelGroupConfig>> groupConfigCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<Boolean>> apiKeyValidityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<LlmModel>> modelPriceCache = new ConcurrentHashMap<>();

    private final long groupConfigTtlMs;
    private final long apiKeyTtlMs;
    private final long modelPriceTtlMs;
    private final MeterRegistry meterRegistry;

    public HotPathCacheService(LuminaProperties properties, MeterRegistry meterRegistry) {
        this.groupConfigTtlMs = properties.getCache().getGroupConfigTtlSeconds() * 1000L;
        this.apiKeyTtlMs = properties.getCache().getApiKeyTtlSeconds() * 1000L;
        this.modelPriceTtlMs = properties.getCache().getModelPriceTtlSeconds() * 1000L;
        this.meterRegistry = meterRegistry;
    }

    public ModelGroupConfig getCachedGroupConfig(String key) {
        return getIfPresent(groupConfigCache, key, "group_config");
    }

    public ModelGroupConfig getGroupConfig(String key, Supplier<ModelGroupConfig> loader) {
        return getOrLoad(groupConfigCache, key, groupConfigTtlMs, loader, "group_config");
    }

    public Boolean getCachedApiKeyValidity(String apiKey) {
        return getIfPresent(apiKeyValidityCache, apiKey, "api_key");
    }

    public Boolean getApiKeyValidity(String apiKey, Supplier<Boolean> loader) {
        return getOrLoad(apiKeyValidityCache, apiKey, apiKeyTtlMs, loader, "api_key");
    }

    public LlmModel getCachedModelPrice(String modelName) {
        return getIfPresent(modelPriceCache, modelName, "model_price");
    }

    public LlmModel getModelPrice(String modelName, Supplier<LlmModel> loader) {
        return getOrLoad(modelPriceCache, modelName, modelPriceTtlMs, loader, "model_price");
    }

    public void invalidateGroupConfig(String key) {
        groupConfigCache.remove(key);
        meterRegistry.counter("lumina_cache_invalidations_total", "cache", "group_config").increment();
    }

    public void invalidateAllGroupConfigs() {
        groupConfigCache.clear();
        meterRegistry.counter("lumina_cache_invalidations_total", "cache", "group_config").increment();
    }

    public void invalidateApiKey(String apiKey) {
        apiKeyValidityCache.remove(apiKey);
        meterRegistry.counter("lumina_cache_invalidations_total", "cache", "api_key").increment();
    }

    public void invalidateAllApiKeys() {
        apiKeyValidityCache.clear();
        meterRegistry.counter("lumina_cache_invalidations_total", "cache", "api_key").increment();
    }

    public void invalidateModelPrice(String modelName) {
        modelPriceCache.remove(modelName);
        meterRegistry.counter("lumina_cache_invalidations_total", "cache", "model_price").increment();
    }

    public void invalidateAllModelPrices() {
        modelPriceCache.clear();
        meterRegistry.counter("lumina_cache_invalidations_total", "cache", "model_price").increment();
    }

    private <T> T getIfPresent(ConcurrentHashMap<String, CacheEntry<T>> cache, String key, String cacheName) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            meterRegistry.counter("lumina_cache_lookups_total", "cache", cacheName, "result", "miss").increment();
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            meterRegistry.counter("lumina_cache_lookups_total", "cache", cacheName, "result", "expired").increment();
            return null;
        }
        meterRegistry.counter("lumina_cache_lookups_total", "cache", cacheName, "result", "hit").increment();
        return entry.value();
    }

    private <T> T getOrLoad(
            ConcurrentHashMap<String, CacheEntry<T>> cache,
            String key,
            long ttlMs,
            Supplier<T> loader,
            String cacheName
    ) {
        T cached = getIfPresent(cache, key, cacheName);
        if (cached != null || cache.containsKey(key)) {
            return cached;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        T loaded = loader.get();
        sample.stop(meterRegistry.timer("lumina_cache_load_duration", "cache", cacheName));
        if (loaded == null) {
            cache.remove(key);
            meterRegistry.counter("lumina_cache_loads_total", "cache", cacheName, "result", "null").increment();
            return null;
        }

        cache.put(key, new CacheEntry<>(loaded, System.currentTimeMillis() + ttlMs));
        meterRegistry.counter("lumina_cache_loads_total", "cache", cacheName, "result", "loaded").increment();
        return loaded;
    }

    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
