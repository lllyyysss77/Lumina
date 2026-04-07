package com.lumina;

import com.lumina.config.LuminaProperties;
import com.lumina.filter.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class RateLimitFilterTest {

    @Mock
    private LuminaProperties luminaProperties;

    @Mock
    private LuminaProperties.RateLimit rateLimit;

    @Mock
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private WebFilterChain filterChain;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(luminaProperties.getRateLimit()).thenReturn(rateLimit);
        when(reactiveStringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void testRateLimitNotEnabled() {
        when(rateLimit.isEnabled()).thenReturn(false);

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/models"));
        
        rateLimitFilter.filter(exchange, filterChain).block();

        verify(filterChain, times(1)).filter(exchange);
        verify(reactiveStringRedisTemplate, never()).opsForValue();
    }

    @Test
    void testRateLimitEnabledBelowLimit() {
        when(rateLimit.isEnabled()).thenReturn(true);
        when(rateLimit.getRequestsPerMinute()).thenReturn(5);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(reactiveStringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/models"));
        exchange.getAttributes().put("API_KEY", "test-key");

        rateLimitFilter.filter(exchange, filterChain).block();

        verify(filterChain, times(1)).filter(exchange);
        verify(valueOperations, times(1)).increment(anyString());
        verify(reactiveStringRedisTemplate, times(1)).expire(anyString(), any(Duration.class));
    }

    @Test
    void testRateLimitExceeded() {
        when(rateLimit.isEnabled()).thenReturn(true);
        when(rateLimit.getRequestsPerMinute()).thenReturn(5);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(6L));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/models"));
        exchange.getAttributes().put("API_KEY", "test-key");

        rateLimitFilter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(exchange);
        assert exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }
}
