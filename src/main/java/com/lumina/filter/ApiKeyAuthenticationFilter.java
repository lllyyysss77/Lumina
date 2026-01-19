package com.lumina.filter;

import com.lumina.service.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@Order(-1)
public class ApiKeyAuthenticationFilter implements WebFilter {

    @Autowired
    private ApiKeyService apiKeyService;

    private static final String X_GOOG_API_KEY = "x-goog-api-key";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 只处理 /v1/** 和 /v1beta/** 路径
        if (!path.startsWith("/v1/") && !path.startsWith("/v1beta/")) {
            return chain.filter(exchange);
        }

        String apiKey = extractApiKey(exchange, path);

        if (!StringUtils.hasText(apiKey)) {
            log.warn("Missing API key for path: {}", path);
            return unauthorized(exchange, "Missing API key");
        }

        return apiKeyService.validateApiKey(apiKey)
                .flatMap(isValid -> {
                    if (isValid) {
                        log.debug("API key validated successfully for path: {}", path);
                        return chain.filter(exchange);
                    } else {
                        log.warn("Invalid API key for path: {}", path);
                        return unauthorized(exchange, "Invalid API key");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error validating API key for path: {}", path, e);
                    return unauthorized(exchange, "Authentication error");
                });
    }

    private String extractApiKey(ServerWebExchange exchange, String path) {
        // /v1beta/** 使用 x-goog-api-key
        if (path.startsWith("/v1beta/")) {
            return exchange.getRequest().getHeaders().getFirst(X_GOOG_API_KEY);
        }

        // /v1/** 使用 Authorization 请求头（Bearer token 格式）
        if (path.startsWith("/v1/")) {
            String authorization = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION);
            if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
                return authorization.substring(BEARER_PREFIX.length());
            }
        }

        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"%s\"}}",
                message
        );

        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }
}
