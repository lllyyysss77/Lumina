package com.lumina.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.state.ProviderRuntimeState;
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
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailoverService {

    private final ProviderStateRegistry providerStateRegistry;

    public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig) {
        List<ModelGroupConfigItem> candidates = modelGroupConfig.getItems()
                .stream()
                .filter(item -> {
                    ProviderRuntimeState state = providerStateRegistry.get(
                            generateProviderId(item),
                            item.getWeight() != null ? item.getWeight() : 1
                    );
                    return state.isAvailable() && state.getCurrentWeight() > 0;
                })
                .toList();

        if (candidates.isEmpty()) {
            throw new RuntimeException("没有可用的Provider（全部熔断或权重为0）");
        }

        return weightedRandomSelect(candidates);
    }

    private ModelGroupConfigItem weightedRandomSelect(List<ModelGroupConfigItem> candidates) {
        int totalWeight = candidates.stream()
                .mapToInt(item -> {
                    ProviderRuntimeState state = providerStateRegistry.get(
                            generateProviderId(item),
                            item.getWeight() != null ? item.getWeight() : 1
                    );
                    return state.getCurrentWeight();
                })
                .sum();

        if (totalWeight == 0) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;

        for (ModelGroupConfigItem item : candidates) {
            ProviderRuntimeState state = providerStateRegistry.get(
                    generateProviderId(item),
                    item.getWeight() != null ? item.getWeight() : 1
            );
            acc += state.getCurrentWeight();
            if (r < acc) {
                return item;
            }
        }

        return candidates.get(candidates.size() - 1);
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

        ProviderRuntimeState state = providerStateRegistry.get(providerId,
                item.getWeight() != null ? item.getWeight() : 1);

        log.debug("尝试使用Provider: {}, 权重: {}, 连续失败: {}",
                providerId, state.getCurrentWeight(), state.getConsecutiveFailures());

        return callSupplier.get()
                .doOnSuccess(response -> {
                    state.recordSuccess();
                    log.debug("Provider {} 调用成功，当前权重: {}", providerId, state.getCurrentWeight());
                })
                .onErrorResume(error -> {
                    log.warn("Provider {} 调用失败: {}, 连续失败: {}",
                            providerId, error.getMessage(), state.getConsecutiveFailures());
                    state.recordFailure();
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

        ProviderRuntimeState state = providerStateRegistry.get(providerId,
                item.getWeight() != null ? item.getWeight() : 1);

        log.debug("尝试使用Provider(流式): {}, 权重: {}, 连续失败: {}",
                providerId, state.getCurrentWeight(), state.getConsecutiveFailures());

        java.util.concurrent.atomic.AtomicBoolean firstChunk = new java.util.concurrent.atomic.AtomicBoolean(true);

        return callSupplier.get()
                .doOnNext(event -> firstChunk.compareAndSet(true, false))
                .doOnComplete(() -> {
                    state.recordSuccess();
                    log.debug("Provider {} 流式调用完成，当前权重: {}", providerId, state.getCurrentWeight());
                })
                .onErrorResume(error -> {
                    if (firstChunk.get()) {
                        log.warn("Provider {} 流式调用首包失败: {}, 连续失败: {}",
                                providerId, error.getMessage(), state.getConsecutiveFailures());
                        state.recordFailure();
                        return executeWithFailoverFlux(callSupplier, group, tried);
                    } else {
                        log.error("Provider {} 流式传输中途失败: {}", providerId, error.getMessage());
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