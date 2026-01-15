package com.lumina.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.state.CircuitBreaker;
import com.lumina.state.ProviderRuntimeState;
import com.lumina.state.ProviderScoreCalculator;
import com.lumina.state.ProviderStateRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailoverService {

    private final ProviderStateRegistry providerStateRegistry;
    private final ProviderScoreCalculator scoreCalculator;
    private final CircuitBreaker circuitBreaker;

    public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig) {
        return selectAvailableProvider(modelGroupConfig, java.util.Collections.emptySet());
    }

    public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig, Set<String> excludeIds) {
        List<ModelGroupConfigItem> candidates = modelGroupConfig.getItems()
                .stream()
                .filter(item -> {
                    String id = generateProviderId(item);
                    if (excludeIds.contains(id)) {
                        return false;
                    }
                    ProviderRuntimeState stats = providerStateRegistry.get(id);
                    if (stats.getProviderName() == null) {
                        stats.setProviderName(item.getProviderName());
                    }
                    return circuitBreaker.allowRequest(stats);
                })
                .toList();

        if (candidates.isEmpty()) {
            throw new RuntimeException("所有Provider已熔断、不可用或已尝试过");
        }

        // 权重随机策略：评分越高被随机到的概率越大
        double totalScore = candidates.stream()
                .mapToDouble(item -> Math.max(providerStateRegistry.get(generateProviderId(item)).getScore(), 0.0))
                .sum();

        if (totalScore <= 0) {
            // 如果所有评分都为 0，退化为等概率随机
            return candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        double randomValue = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * totalScore;
        double cumulativeScore = 0;
        for (ModelGroupConfigItem item : candidates) {
            cumulativeScore += Math.max(providerStateRegistry.get(generateProviderId(item)).getScore(), 0.0);
            if (randomValue <= cumulativeScore) {
                return item;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    public Mono<ObjectNode> executeWithFailoverMono(
            java.util.function.Function<ModelGroupConfigItem, Mono<ObjectNode>> callFunction,
            ModelGroupConfig group
    ) {
        return executeWithFailoverMono(callFunction, group, new HashSet<>());
    }

    private Mono<ObjectNode> executeWithFailoverMono(
            java.util.function.Function<ModelGroupConfigItem, Mono<ObjectNode>> callFunction,
            ModelGroupConfig group,
            Set<String> tried
    ) {
        ModelGroupConfigItem item;
        try {
            item = selectAvailableProvider(group, tried);
        } catch (Exception e) {
            return Mono.error(e);
        }
        
        String providerId = generateProviderId(item);
        tried.add(providerId);

        ProviderRuntimeState state = providerStateRegistry.get(providerId);

        log.debug("尝试使用Provider: {}, 当前评分: {}", providerId, state.getScore());

        long startTime = System.currentTimeMillis();

        return callFunction.apply(item)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    scoreCalculator.update(state, true, duration);
                    circuitBreaker.onSuccess(state);
                    log.debug("Provider {} 调用成功，耗时: {}ms, 新评分: {}", providerId, duration, state.getScore());
                })
                .onErrorResume(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("Provider {} 调用失败: {}, 耗时: {}ms", providerId, error.getMessage(), duration);
                    scoreCalculator.update(state, false, duration);
                    circuitBreaker.onFailure(state);
                    return executeWithFailoverMono(callFunction, group, tried);
                });
    }

    public Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Function<ModelGroupConfigItem, Flux<ServerSentEvent<String>>> callFunction,
            ModelGroupConfig group
    ) {
        return executeWithFailoverFlux(callFunction, group, new HashSet<>());
    }

    private Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Function<ModelGroupConfigItem, Flux<ServerSentEvent<String>>> callFunction,
            ModelGroupConfig group,
            Set<String> tried
    ) {
        ModelGroupConfigItem item;
        try {
            item = selectAvailableProvider(group, tried);
        } catch (Exception e) {
            return Flux.error(e);
        }

        String providerId = generateProviderId(item);
        tried.add(providerId);

        ProviderRuntimeState state = providerStateRegistry.get(providerId);

        log.debug("尝试使用Provider(流式): {}, 当前评分: {}", providerId, state.getScore());

        long startTime = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicBoolean firstChunk = new java.util.concurrent.atomic.AtomicBoolean(true);

        return callFunction.apply(item)
                .doOnNext(event -> firstChunk.compareAndSet(true, false))
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    scoreCalculator.update(state, true, duration);
                    circuitBreaker.onSuccess(state);
                    log.debug("Provider {} 流式调用完成，耗时: {}ms, 新评分: {}", providerId, duration, state.getScore());
                })
                .onErrorResume(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (firstChunk.get()) {
                        log.warn("Provider {} 流式调用首包失败: {}, 耗时: {}ms", providerId, error.getMessage(), duration);
                        scoreCalculator.update(state, false, duration);
                        circuitBreaker.onFailure(state);
                        return executeWithFailoverFlux(callFunction, group, tried);
                    } else {
                        log.error("Provider {} 流式传输中途失败: {}", providerId, error.getMessage());
                        scoreCalculator.update(state, false, duration);
                        circuitBreaker.onFailure(state);
                        return Flux.error(error);
                    }
                });
    }

    private String generateProviderId(ModelGroupConfigItem item) {
        return String.format("%s_%s_%s",
                item.getBaseUrl(),
                item.getApiKey() != null ? item.getApiKey().hashCode() : "null",
                item.getModelName());
    }
}
