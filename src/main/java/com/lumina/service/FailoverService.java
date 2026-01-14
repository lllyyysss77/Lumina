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
        List<ModelGroupConfigItem> candidates = modelGroupConfig.getItems()
                .stream()
                .filter(item -> {
                    ProviderRuntimeState stats = providerStateRegistry.get(generateProviderId(item));
                    if (stats.getProviderName() == null) {
                        stats.setProviderName(item.getProviderName());
                    }
                    return circuitBreaker.allowRequest(stats);
                })
                .sorted((a, b) -> {
                    double sa = providerStateRegistry.get(generateProviderId(a)).getScore();
                    double sb = providerStateRegistry.get(generateProviderId(b)).getScore();
                    return Double.compare(sb, sa);
                })
                .toList();

        if (candidates.isEmpty()) {
            throw new RuntimeException("所有Provider已熔断或不可用");
        }

        // 引入同分随机策略：找到所有评分与最高分相等的 Provider
        double bestScore = providerStateRegistry.get(generateProviderId(candidates.get(0))).getScore();
        List<ModelGroupConfigItem> topCandidates = candidates.stream()
                .filter(item -> providerStateRegistry.get(generateProviderId(item)).getScore() >= bestScore)
                .toList();

        if (topCandidates.size() > 1) {
            return topCandidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(topCandidates.size()));
        }

        return topCandidates.get(0);
    }

    public Mono<ObjectNode> executeWithFailoverMono(
            java.util.function.Supplier<Mono<ObjectNode>> callSupplier,
            ModelGroupConfig group
    ) {
        return executeWithFailoverMono(callSupplier, group, new HashSet<>());
    }

    private Mono<ObjectNode> executeWithFailoverMono(
            java.util.function.Supplier<Mono<ObjectNode>> callSupplier,
            ModelGroupConfig group,
            Set<String> tried
    ) {
        ModelGroupConfigItem item = selectAvailableProvider(group);
        String providerId = generateProviderId(item);

        if (!tried.add(providerId)) {
            return Mono.error(new RuntimeException("所有Provider都尝试过，仍然失败"));
        }

        ProviderRuntimeState state = providerStateRegistry.get(providerId);

        log.debug("尝试使用Provider: {}, 当前评分: {}", providerId, state.getScore());

        long startTime = System.currentTimeMillis();

        return callSupplier.get()
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
                    return executeWithFailoverMono(callSupplier, group, tried);
                });
    }

    public Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Supplier<Flux<ServerSentEvent<String>>> callSupplier,
            ModelGroupConfig group
    ) {
        return executeWithFailoverFlux(callSupplier, group, new HashSet<>());
    }

    private Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Supplier<Flux<ServerSentEvent<String>>> callSupplier,
            ModelGroupConfig group,
            Set<String> tried
    ) {
        ModelGroupConfigItem item = selectAvailableProvider(group);
        String providerId = generateProviderId(item);

        if (!tried.add(providerId)) {
            return Flux.error(new RuntimeException("所有Provider都尝试过，仍然失败"));
        }

        ProviderRuntimeState state = providerStateRegistry.get(providerId);

        log.debug("尝试使用Provider(流式): {}, 当前评分: {}", providerId, state.getScore());

        long startTime = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicBoolean firstChunk = new java.util.concurrent.atomic.AtomicBoolean(true);

        return callSupplier.get()
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
                        return executeWithFailoverFlux(callSupplier, group, tried);
                    } else {
                        log.error("Provider {} 流式传输中途失败: {}", providerId, error.getMessage());
                        // 中途失败通常不作为重试的触发点，但可能也需要计入失败？
                        // 暂时按原逻辑处理，不重试
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
