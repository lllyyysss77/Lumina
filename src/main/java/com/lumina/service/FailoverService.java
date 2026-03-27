package com.lumina.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.config.CircuitBreakerConfigResolver;
import com.lumina.config.EffectiveCircuitBreakerConfig;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.exception.BulkheadFullException;
import com.lumina.exception.MaxFailoverExceededException;
import com.lumina.metrics.RelayMetrics;
import com.lumina.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailoverService {

    private final ProviderStateRegistry providerStateRegistry;
    private final ProviderScoreCalculator scoreCalculator;
    private final CircuitBreaker circuitBreaker;
    private final CircuitBreakerConfigResolver configResolver;
    private final RelayMetrics relayMetrics;

    private static final int TOP_K = 3;
    private static final double SOFTMAX_T = 10.0;
    private static final double HALF_OPEN_WEIGHT_FACTOR = 0.5;

    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * 根据异常分类错误类型
     */
    public FailureType classifyError(Throwable throwable) {
        if (throwable == null) {
            return FailureType.SUCCESS;
        }

        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        if (throwable instanceof TimeoutException || cause instanceof TimeoutException ||
            cause instanceof SocketTimeoutException) {
            return FailureType.TIMEOUT;
        }

        if (cause instanceof ConnectException) {
            return FailureType.CONNECT;
        }

        if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
            return FailureType.DNS;
        }

        if (cause instanceof SSLException) {
            return FailureType.TLS;
        }

        if (throwable instanceof WebClientRequestException) {
            Throwable rootCause = ((WebClientRequestException) throwable).getRootCause();
            if (rootCause instanceof UnknownHostException) {
                return FailureType.DNS;
            }
            if (rootCause instanceof ConnectException) {
                return FailureType.CONNECT;
            }
            if (rootCause instanceof SSLException) {
                return FailureType.TLS;
            }
            return FailureType.CONNECT;
        }

        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException responseEx = (WebClientResponseException) throwable;
            int statusCode = responseEx.getStatusCode().value();

            if (statusCode == 429) {
                return FailureType.HTTP_429;
            }
            if (statusCode >= 500 && statusCode < 600) {
                return FailureType.HTTP_5XX;
            }
            if (statusCode >= 400 && statusCode < 500) {
                return FailureType.HTTP_4XX;
            }
        }

        String message = throwable.getMessage();
        if (message != null && (message.contains("JSON") || message.contains("decode") || message.contains("parse"))) {
            return FailureType.DECODE;
        }

        return FailureType.UNKNOWN;
    }

    /**
     * 解析 Provider 的生效配置
     */
    private EffectiveCircuitBreakerConfig resolveConfig(ModelGroupConfig group, ModelGroupConfigItem item, int requestHash) {
        String providerId = generateProviderId(item);
        return configResolver.resolve(
                group.getId(),
                group.getCircuitBreakerConfig(),
                providerId,
                item.getCircuitBreakerConfig(),
                requestHash
        );
    }

    public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig) {
        return selectAvailableProvider(modelGroupConfig, java.util.Collections.emptySet(), 0);
    }

    public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig, Set<String> excludeIds) {
        return selectAvailableProvider(modelGroupConfig, excludeIds, 0);
    }

    public ModelGroupConfigItem selectAvailableProvider(ModelGroupConfig modelGroupConfig, Set<String> excludeIds, int requestHash) {
        // 轮询模式：直接轮询，不做熔断过滤
        Integer balanceMode = modelGroupConfig.getBalanceMode();
        if (balanceMode != null && balanceMode == 1) {
            return selectByRoundRobin(modelGroupConfig.getItems(), excludeIds, modelGroupConfig.getId());
        }

        // SAPR 模式（默认）
        // 1. 过滤可用 Provider
        List<ModelGroupConfigItem> available = modelGroupConfig.getItems()
                .stream()
                .filter(item -> {
                    String id = generateProviderId(item);
                    if (excludeIds.contains(id)) {
                        relayMetrics.recordProviderSkipped("excluded");
                        return false;
                    }
                    ProviderRuntimeState stats = providerStateRegistry.get(id);
                    if (stats.getProviderName() == null) {
                        stats.setProviderName(item.getProviderName());
                    }
                    if (stats.getModelName() == null) {
                        stats.setModelName(item.getModelName());
                    }
                    // 使用解析后的配置判断是否允许请求
                    EffectiveCircuitBreakerConfig effectiveConfig = resolveConfig(modelGroupConfig, item, requestHash);
                    boolean allowed = circuitBreaker.allowRequest(stats, effectiveConfig);
                    if (!allowed) {
                        relayMetrics.recordProviderSkipped("circuit_" + stats.getCircuitState().name().toLowerCase());
                    }
                    return allowed;
                })
                .toList();

        if (available.isEmpty()) {
            // 保底：降级到轮询，忽略 excludeIds，所有 Provider 都参与轮询
            log.warn("Group {} 所有 Provider 熔断或不可用，降级到轮询保底策略", modelGroupConfig.getId());
            relayMetrics.recordFallbackToRoundRobin();
            return selectByRoundRobin(modelGroupConfig.getItems(), Collections.emptySet(), modelGroupConfig.getId());
        }

        relayMetrics.recordSelection("sapr");

        // 2. 按 selection score 降序排序（健康分 + weight 先验）
        List<ModelGroupConfigItem> sorted = available.stream()
                .sorted((a, b) -> {
                    double sa = getSelectionScore(a);
                    double sb = getSelectionScore(b);
                    return Double.compare(sb, sa);
                })
                .toList();

        // 3. 取 Top-K
        List<ModelGroupConfigItem> topK = sorted.subList(0, Math.min(TOP_K, sorted.size()));

        // 4. 计算 Softmax 权重
        double[] weights = new double[topK.size()];
        double sum = 0.0;

        for (int i = 0; i < topK.size(); i++) {
            double score = getSelectionScore(topK.get(i));
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

        return topK.get(0);
    }

    /**
     * 轮询策略选择 Provider
     */
    private ModelGroupConfigItem selectByRoundRobin(List<ModelGroupConfigItem> items, Set<String> excludeIds, String groupId) {
        List<ModelGroupConfigItem> candidates = items.stream()
                .filter(item -> !excludeIds.contains(generateProviderId(item)))
                .toList();

        if (candidates.isEmpty()) {
            throw new RuntimeException("所有 Provider 已尝试过，轮询无可用候选");
        }

        String key = groupId != null ? groupId : "default";
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % candidates.size());
        relayMetrics.recordSelection("round_robin");
        return candidates.get(index);
    }

    private double getEffectiveScore(ProviderRuntimeState state) {
        double score = state.getScore();
        if (state.getCircuitState() == CircuitState.HALF_OPEN) {
            score *= HALF_OPEN_WEIGHT_FACTOR;
        }
        return score;
    }

    private double getSelectionScore(ModelGroupConfigItem item) {
        ProviderRuntimeState state = providerStateRegistry.get(generateProviderId(item));
        double score = getEffectiveScore(state);
        int configuredWeight = Math.max(1, item.getWeight() == null ? 1 : item.getWeight());
        return score + SOFTMAX_T * Math.log(configuredWeight);
    }

    public Mono<ObjectNode> executeWithFailoverMono(
            java.util.function.Function<ModelGroupConfigItem, Mono<ObjectNode>> callFunction,
            ModelGroupConfig group,
            Integer timeoutMs
    ) {
        // 生成请求哈希用于灰度一致性
        int requestHash = ThreadLocalRandom.current().nextInt();
        return executeWithFailoverMono(callFunction, group, new HashSet<>(), timeoutMs, 0, requestHash);
    }

    private Mono<ObjectNode> executeWithFailoverMono(
            java.util.function.Function<ModelGroupConfigItem, Mono<ObjectNode>> callFunction,
            ModelGroupConfig group,
            Set<String> tried,
            Integer timeoutMs,
            int attemptCount,
            int requestHash
    ) {
        // 解析 Group 级别配置获取 maxFailoverAttempts
        EffectiveCircuitBreakerConfig groupConfig = configResolver.resolve(
                group.getId(), group.getCircuitBreakerConfig());

        // 检查 Failover 次数限制
        if (attemptCount >= groupConfig.getMaxFailoverAttempts()) {
            relayMetrics.recordMaxFailoverExceeded(false);
            relayMetrics.recordFailoverDepth(attemptCount);
            return Mono.error(new MaxFailoverExceededException(attemptCount, groupConfig.getMaxFailoverAttempts()));
        }

        ModelGroupConfigItem item;
        try {
            item = selectAvailableProvider(group, tried, requestHash);
        } catch (Exception e) {
            relayMetrics.recordNoProviderAvailable(false);
            relayMetrics.recordFailoverDepth(attemptCount);
            return Mono.error(e);
        }

        String providerId = generateProviderId(item);
        tried.add(providerId);
        ProviderRuntimeState state = providerStateRegistry.get(providerId);
        if (state.getProviderName() == null) {
            state.setProviderName(item.getProviderName());
        }
        if (state.getModelName() == null) {
            state.setModelName(item.getModelName());
        }

        // 解析 Provider 级别的生效配置
        EffectiveCircuitBreakerConfig effectiveConfig = resolveConfig(group, item, requestHash);

        log.debug("尝试使用Provider(非流式): {}, 当前评分: {}, 尝试次数: {}, 配置来源: {}",
                providerId, state.getScore(), attemptCount + 1, effectiveConfig.getSourceLevel());

        // 检查并发舱壁
        ProviderBulkhead bulkhead = state.getBulkhead();
        bulkhead.setMaxConcurrent(effectiveConfig.getMaxConcurrentRequestsPerProvider());
        if (!bulkhead.tryAcquire()) {
            log.warn("Provider {} 并发已满，当前: {}/{}, 尝试 Failover",
                    providerId, bulkhead.getCurrentConcurrent(), bulkhead.getMaxConcurrent());
            relayMetrics.recordBulkheadRejection(false);
            relayMetrics.recordFailoverAttempt(false, attemptCount + 1);
            return executeWithFailoverMono(callFunction, group, tried, timeoutMs, attemptCount + 1, requestHash);
        }

        long startTime = System.currentTimeMillis();
        Mono<ObjectNode> result = callFunction.apply(item);

        return result
                .doOnSuccess(response -> {
                    bulkhead.release();
                    long duration = System.currentTimeMillis() - startTime;
                    scoreCalculator.update(state, FailureType.SUCCESS, duration);
                    circuitBreaker.onSuccess(state, effectiveConfig);
                    relayMetrics.recordFailoverDepth(attemptCount);
                    log.debug("Provider {} 调用成功，耗时: {}ms, 新评分: {}", providerId, duration, state.getScore());
                })
                .doOnError(error -> bulkhead.release())
                .onErrorResume(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    FailureType failureType = classifyError(error);

                    if (failureType == FailureType.TIMEOUT) {
                        log.warn("Provider {} 调用超时: 配置={}ms, 实际={}ms", providerId, timeoutMs, duration);
                    } else {
                        log.warn("Provider {} 调用失败: {} (类型: {}), 耗时: {}ms",
                                providerId, error.getMessage(), failureType, duration);
                    }

                    scoreCalculator.update(state, failureType, duration);
                    circuitBreaker.onFailure(state, failureType, effectiveConfig);

                    if (!failureType.shouldFailover()) {
                        log.debug("错误类型 {} 不触发 Failover，直接返回错误", failureType);
                        relayMetrics.recordFailoverDepth(attemptCount);
                        return Mono.error(error);
                    }

                    relayMetrics.recordFailoverSwitch(false, "before_response", failureType.name().toLowerCase());
                    relayMetrics.recordFailoverAttempt(false, attemptCount + 1);
                    return executeWithFailoverMono(callFunction, group, tried, timeoutMs, attemptCount + 1, requestHash);
                });
    }

    public Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Function<ModelGroupConfigItem, Flux<ServerSentEvent<String>>> callFunction,
            ModelGroupConfig group,
            Integer timeoutMs
    ) {
        int requestHash = ThreadLocalRandom.current().nextInt();
        return executeWithFailoverFlux(callFunction, group, new HashSet<>(), timeoutMs, 0, requestHash);
    }

    private Flux<ServerSentEvent<String>> executeWithFailoverFlux(
            java.util.function.Function<ModelGroupConfigItem, Flux<ServerSentEvent<String>>> callFunction,
            ModelGroupConfig group,
            Set<String> tried,
            Integer timeoutMs,
            int attemptCount,
            int requestHash
    ) {
        EffectiveCircuitBreakerConfig groupConfig = configResolver.resolve(
                group.getId(), group.getCircuitBreakerConfig());

        if (attemptCount >= groupConfig.getMaxFailoverAttempts()) {
            relayMetrics.recordMaxFailoverExceeded(true);
            relayMetrics.recordFailoverDepth(attemptCount);
            return Flux.error(new MaxFailoverExceededException(attemptCount, groupConfig.getMaxFailoverAttempts()));
        }

        ModelGroupConfigItem item;
        try {
            item = selectAvailableProvider(group, tried, requestHash);
        } catch (Exception e) {
            relayMetrics.recordNoProviderAvailable(true);
            relayMetrics.recordFailoverDepth(attemptCount);
            return Flux.error(e);
        }

        String providerId = generateProviderId(item);
        tried.add(providerId);
        ProviderRuntimeState state = providerStateRegistry.get(providerId);
        if (state.getProviderName() == null) {
            state.setProviderName(item.getProviderName());
        }
        if (state.getModelName() == null) {
            state.setModelName(item.getModelName());
        }

        EffectiveCircuitBreakerConfig effectiveConfig = resolveConfig(group, item, requestHash);

        log.debug("尝试使用Provider(流式): {}, 当前评分: {}, 尝试次数: {}, 配置来源: {}",
                providerId, state.getScore(), attemptCount + 1, effectiveConfig.getSourceLevel());

        ProviderBulkhead bulkhead = state.getBulkhead();
        bulkhead.setMaxConcurrent(effectiveConfig.getMaxConcurrentRequestsPerProvider());
        if (!bulkhead.tryAcquire()) {
            log.warn("Provider {} 并发已满，当前: {}/{}, 尝试 Failover",
                    providerId, bulkhead.getCurrentConcurrent(), bulkhead.getMaxConcurrent());
            relayMetrics.recordBulkheadRejection(true);
            relayMetrics.recordFailoverAttempt(true, attemptCount + 1);
            return executeWithFailoverFlux(callFunction, group, tried, timeoutMs, attemptCount + 1, requestHash);
        }

        long startTime = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicBoolean firstChunk = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicBoolean bulkheadReleased = new java.util.concurrent.atomic.AtomicBoolean(false);

        Runnable releaseBulkhead = () -> {
            if (bulkheadReleased.compareAndSet(false, true)) {
                bulkhead.release();
            }
        };

        Flux<ServerSentEvent<String>> result = callFunction.apply(item);

        return result
                .doOnNext(event -> firstChunk.compareAndSet(true, false))
                .doOnComplete(() -> {
                    releaseBulkhead.run();
                    long duration = System.currentTimeMillis() - startTime;
                    scoreCalculator.update(state, FailureType.SUCCESS, duration);
                    circuitBreaker.onSuccess(state, effectiveConfig);
                    relayMetrics.recordFailoverDepth(attemptCount);
                    log.debug("Provider {} 流式调用完成，耗时: {}ms, 新评分: {}", providerId, duration, state.getScore());
                })
                .doOnCancel(releaseBulkhead)
                .onErrorResume(error -> {
                    releaseBulkhead.run();
                    long duration = System.currentTimeMillis() - startTime;
                    FailureType failureType = classifyError(error);

                    if (firstChunk.get()) {
                        if (failureType == FailureType.TIMEOUT) {
                            log.warn("Provider {} 流式调用首包超时: 配置={}ms, 实际={}ms", providerId, timeoutMs, duration);
                        } else {
                            log.warn("Provider {} 流式调用首包失败: {} (类型: {}), 耗时: {}ms",
                                    providerId, error.getMessage(), failureType, duration);
                        }

                        scoreCalculator.update(state, failureType, duration);
                        circuitBreaker.onFailure(state, failureType, effectiveConfig);

                        if (!failureType.shouldFailover()) {
                            log.debug("错误类型 {} 不触发 Failover，直接返回错误", failureType);
                            relayMetrics.recordFailoverDepth(attemptCount);
                            return Flux.error(error);
                        }

                        relayMetrics.recordFailoverSwitch(true, "first_chunk", failureType.name().toLowerCase());
                        relayMetrics.recordFailoverAttempt(true, attemptCount + 1);
                        return executeWithFailoverFlux(callFunction, group, tried, timeoutMs, attemptCount + 1, requestHash);
                    } else {
                        log.error("Provider {} 流式传输中途失败: {} (类型: {})", providerId, error.getMessage(), failureType);
                        scoreCalculator.update(state, failureType, duration);
                        circuitBreaker.onFailure(state, failureType, effectiveConfig);
                        relayMetrics.recordFailoverDepth(attemptCount);
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
