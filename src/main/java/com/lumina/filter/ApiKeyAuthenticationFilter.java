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

    private static final String BEARER_PREFIX = "Bearer ";

    // 常见的 API Key 请求头名称列表（按优先级顺序）
    private static final String[] API_KEY_HEADER_NAMES = {
        "Authorization",           // Bearer token 格式
        "X-API-Key",               // Cherry Studio 等
        "X-Api-Key",               // 大小写变体
        "x-goog-api-key",          // Gemini API
        "api-key",                 // 其他客户端
        "API-Key"                  // 大小写变体
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 只处理 /v1/** 和 /v1beta/** 路径
        if (!path.startsWith("/v1/") && !path.startsWith("/v1beta/")) {
            return chain.filter(exchange);
        }

        String apiKey = extractApiKey(exchange);

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

    /**
     * 从请求中提取 API Key
     * 支持多种请求头格式，按优先级顺序尝试
     */
    private String extractApiKey(ServerWebExchange exchange) {
        for (String headerName : API_KEY_HEADER_NAMES) {
            String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);
            if (StringUtils.hasText(headerValue)) {
                // Authorization 头需要去掉 "Bearer " 前缀
                if (headerName.equalsIgnoreCase("Authorization")) {
                    if (headerValue.startsWith(BEARER_PREFIX)) {
                        String apiKey = headerValue.substring(BEARER_PREFIX.length()).trim();
                        if (StringUtils.hasText(apiKey)) {
                            log.debug("Extracted API key from Authorization header");
                            return apiKey;
                        }
                    }
                } else {
                    // 其他头直接使用其值（去除首尾空格）
                    String apiKey = headerValue.trim();
                    if (StringUtils.hasText(apiKey)) {
                        log.debug("Extracted API key from {} header", headerName);
                        return apiKey;
                    }
                }
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
