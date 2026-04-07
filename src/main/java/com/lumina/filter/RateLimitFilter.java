package com.lumina.filter;

import com.lumina.config.LuminaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@Order(0)
public class RateLimitFilter implements WebFilter {

    @Autowired
    private LuminaProperties luminaProperties;

    @Autowired
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!luminaProperties.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/v1/") && !path.startsWith("/v1beta/")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getAttribute("API_KEY");
        if (apiKey == null) {
            return chain.filter(exchange);
        }

        int limit = luminaProperties.getRateLimit().getRequestsPerMinute();
        if (limit <= 0) {
            return chain.filter(exchange);
        }

        long currentMinute = Instant.now().getEpochSecond() / 60;
        String redisKey = "rate_limit:" + apiKey + ":" + currentMinute;

        return reactiveStringRedisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        return reactiveStringRedisTemplate.expire(redisKey, Duration.ofSeconds(60))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > limit) {
                        log.warn("Rate limit exceeded for API key. Limit: {}, Count: {}", limit, count);
                        return tooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.error("Error during rate limiting check", e);
                    // Fallback to allow request if Redis fails
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\":{\"type\":\"requests\",\"message\":\"Rate limit reached for requests\",\"code\":\"rate_limit_exceeded\"}}";

        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }
}
