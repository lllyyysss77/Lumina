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

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailoverService {

    private final ProviderStateRegistry providerStateRegistry;
    private final ProviderScoreCalculator scoreCalculator;
    private final CircuitBreaker circuitBreaker;
    private static final int TOP_K = 3;
    private static final double SOFTMAX_T = 10.0;

        public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig) {
            return selectAvailableProvider(modelGroupConfig, java.util.Collections.emptySet());
        }

        public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig, Set<String> excludeIds) {

            // 1. 过滤可用 Provider (同时排除已尝试过的)
            List<ModelGroupConfigItem> available = modelGroupConfig.getItems()
                    .stream()
                    .filter(item -> {
                        String id = generateProviderId(item);
                        if (excludeIds.contains(id)) {
                            return false;
                        }
                        ProviderRuntimeState stats =
                                providerStateRegistry.get(id);
                        if (stats.getProviderName() == null) {
                            stats.setProviderName(item.getProviderName());
                        }
                        return circuitBreaker.allowRequest(stats);
                    })
                    .toList();

            if (available.isEmpty()) {
                throw new RuntimeException("所有 Provider 已熔断、不可用或已尝试过");
            }

            // 2. 按 score 降序排序
            List<ModelGroupConfigItem> sorted = available.stream()
                    .sorted((a, b) -> {
                        double sa = providerStateRegistry
                                .get(generateProviderId(a))
                                .getScore();
                        double sb = providerStateRegistry
                                .get(generateProviderId(b))
                                .getScore();
                        return Double.compare(sb, sa);
                    })
                    .toList();

            // 3. 取 Top-K
            List<ModelGroupConfigItem> topK = sorted.subList(
                    0,
                    Math.min(TOP_K, sorted.size())
            );

            // 4. 计算 Softmax 权重
            double[] weights = new double[topK.size()];
            double sum = 0.0;

            for (int i = 0; i < topK.size(); i++) {
                double score = providerStateRegistry
                        .get(generateProviderId(topK.get(i)))
                        .getScore();

                // Softmax：exp(score / T)
                double w = Math.exp(score / SOFTMAX_T);
                weights[i] = w;
                sum += w;
            }

            // 5. 按权重随机选择
            double r = ThreadLocalRandom.current().nextDouble() * sum;
            double acc = 0.0;

            for (int i = 0; i < topK.size(); i++) {
                acc += weights[i];
                if (r <= acc) {
                    return topK.get(i);
                }
            }

            // 理论上不会走到这里，兜底返回第一名
            return topK.get(0);
        }

        public Mono<ObjectNode> executeWithFailoverMono(
                java.util.function.Function<ModelGroupConfigItem, Mono<ObjectNode>> callFunction,
                ModelGroupConfig group,
                Integer timeoutMs
        ) {
            return executeWithFailoverMono(callFunction, group, new HashSet<>(), timeoutMs);
        }

        private Mono<ObjectNode> executeWithFailoverMono(
                java.util.function.Function<ModelGroupConfigItem, Mono<ObjectNode>> callFunction,
                ModelGroupConfig group,
                Set<String> tried,
                Integer timeoutMs
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

        log.debug("尝试使用Provider(非流式): {}, 当前评分: {}", providerId, state.getScore());

        long startTime = System.currentTimeMillis();

        Mono<ObjectNode> result = callFunction.apply(item);

        if (timeoutMs != null && timeoutMs > 0) {
            result = result.timeout(Duration.ofMillis(timeoutMs));
        }

        return result
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    scoreCalculator.update(state, true, duration);
                    circuitBreaker.onSuccess(state);
                    log.debug("Provider {} 调用成功，耗时: {}ms, 新评分: {}", providerId, duration, state.getScore());
                })
                .onErrorResume(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (error instanceof TimeoutException) {
                        log.warn("Provider {} 调用超时: 配置={}ms, 实际={}ms", providerId, timeoutMs, duration);
                    } else {
                        log.warn("Provider {} 调用失败: {}, 耗时: {}ms", providerId, error.getMessage(), duration);
                    }
                    scoreCalculator.update(state, false, duration);
                    circuitBreaker.onFailure(state);
                    return executeWithFailoverMono(callFunction, group, tried, timeoutMs);
                });
    }

    public Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Function<ModelGroupConfigItem, Flux<ServerSentEvent<String>>> callFunction,
            ModelGroupConfig group,
            Integer timeoutMs
    ) {
        return executeWithFailoverFlux(callFunction, group, new HashSet<>(), timeoutMs);
    }

    private Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Function<ModelGroupConfigItem, Flux<ServerSentEvent<String>>> callFunction,
            ModelGroupConfig group,
            Set<String> tried,
            Integer timeoutMs
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

        Flux<ServerSentEvent<String>> result = callFunction.apply(item);

        if (timeoutMs != null && timeoutMs > 0) {
            result = result.timeout(Duration.ofMillis(timeoutMs));
        }

        return result
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
                        if (error instanceof TimeoutException) {
                            log.warn("Provider {} 流式调用首包超时: 配置={}ms, 实际={}ms", providerId, timeoutMs, duration);
                        } else {
                            log.warn("Provider {} 流式调用首包失败: {}, 耗时: {}ms", providerId, error.getMessage(), duration);
                        }
                        scoreCalculator.update(state, false, duration);
                        circuitBreaker.onFailure(state);
                        return executeWithFailoverFlux(callFunction, group, tried, timeoutMs);
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
