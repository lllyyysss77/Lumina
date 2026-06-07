package com.lumina.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.config.CircuitBreakerConfig;
import com.lumina.config.CircuitBreakerConfigResolver;
import com.lumina.config.LuminaProperties;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.mapper.ProviderRuntimeStatsMapper;
import com.lumina.metrics.RelayMetrics;
import com.lumina.state.CircuitBreaker;
import com.lumina.state.ProviderScoreCalculator;
import com.lumina.state.ProviderStateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FailoverServiceTest {

    private static final int ROUND_ROBIN_MODE = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private ProviderScoreCalculator scoreCalculator;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private RelayMetrics relayMetrics;

    @Mock
    private ProviderRuntimeStatsMapper providerRuntimeStatsMapper;

    private FailoverService failoverService;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig circuitBreakerConfig = new CircuitBreakerConfig();
        ProviderStateRegistry providerStateRegistry = new ProviderStateRegistry(providerRuntimeStatsMapper, circuitBreakerConfig);
        CircuitBreakerConfigResolver configResolver = new CircuitBreakerConfigResolver(circuitBreakerConfig);

        failoverService = new FailoverService(
                providerStateRegistry,
                scoreCalculator,
                circuitBreaker,
                configResolver,
                relayMetrics,
                new LuminaProperties()
        );
    }

    @Test
    void roundRobinSuccessDoesNotUpdateScoreOrCircuitBreaker() {
        ObjectNode response = mapper.createObjectNode().put("ok", true);

        ObjectNode actual = failoverService.executeWithFailoverMono(
                provider -> Mono.just(response),
                roundRobinGroup("rr-success"),
                1000
        ).block(Duration.ofSeconds(1));

        assertEquals(response, actual);
        verifyNoInteractions(scoreCalculator, circuitBreaker);
    }

    @Test
    void roundRobinFailoverDoesNotUpdateScoreOrCircuitBreaker() {
        AtomicInteger calls = new AtomicInteger();
        List<String> providerNames = new ArrayList<>();

        ObjectNode actual = failoverService.executeWithFailoverMono(
                provider -> {
                    providerNames.add(provider.getProviderName());
                    if (calls.getAndIncrement() == 0) {
                        return Mono.error(new RuntimeException("first provider failed"));
                    }
                    return Mono.just(mapper.createObjectNode().put("provider", provider.getProviderName()));
                },
                roundRobinGroup("rr-failover"),
                1000
        ).block(Duration.ofSeconds(1));

        assertEquals("provider-b", actual.get("provider").asText());
        assertEquals(List.of("provider-a", "provider-b"), providerNames);
        verifyNoInteractions(scoreCalculator, circuitBreaker);
    }

    private ModelGroupConfig roundRobinGroup(String id) {
        ModelGroupConfig group = new ModelGroupConfig();
        group.setId(id);
        group.setName(id);
        group.setBalanceMode(ROUND_ROBIN_MODE);
        group.setItems(List.of(provider("provider-a"), provider("provider-b")));
        return group;
    }

    private ModelGroupConfigItem provider(String name) {
        ModelGroupConfigItem item = new ModelGroupConfigItem();
        item.setProviderName(name);
        item.setModelName("gpt-test");
        item.setBaseUrl("https://" + name + ".example.com");
        item.setApiKey(name + "-key");
        return item;
    }
}
