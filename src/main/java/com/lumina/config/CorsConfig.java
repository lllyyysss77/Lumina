package com.lumina.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORS 配置。
 * <p>
 * 出于安全考虑，默认不允许任何跨域来源；生产部署应通过
 * {@code LUMINA_ALLOWED_ORIGINS} 环境变量（或 {@code lumina.cors.allowed-origins}
 * 配置项）显式声明受信任的 origin 列表，例如：
 * <pre>{@code
 *   LUMINA_ALLOWED_ORIGINS=https://admin.example.com,https://api.example.com
 * }</pre>
 * <p>
 * 绝不允许同时使用通配符 {@code *} 与 {@code allowCredentials=true}，这一组合
 * 违反 CORS 规范，且会在任意攻击者站点上放行携带 Cookie 的跨域请求。
 */
@Configuration
public class CorsConfig {

    @Value("${lumina.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${lumina.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = parseOrigins(allowedOrigins);

        boolean hasWildcard = origins.stream().anyMatch(o -> o.equals("*"));
        if (hasWildcard && allowCredentials) {
            // 规范不允许 "*" + credentials 组合，必须二选一。默认优先保护凭证。
            throw new IllegalStateException(
                    "Invalid CORS configuration: allowed-origins contains '*' while allow-credentials=true. "
                            + "Either list explicit origins or set lumina.cors.allow-credentials=false.");
        }

        if (origins.isEmpty()) {
            // 默认：无跨域白名单。前端由后端同源托管，不需要 CORS。
            // 仍注册一个空配置，避免上游拦截器拿到 null。
            config.setAllowedOrigins(List.of());
        } else if (hasWildcard) {
            // 允许 "*" 时 credentials 必须为 false（上面已校验）
            config.addAllowedOrigin("*");
        } else {
            // 支持带通配符的 pattern，例如 https://*.example.com
            config.setAllowedOriginPatterns(origins);
        }

        config.setAllowCredentials(allowCredentials && !hasWildcard);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("Authorization");
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/v1/**", config);
        source.registerCorsConfiguration("/v1beta/**", config);
        source.registerCorsConfiguration("/actuator/**", config);

        return source;
    }

    private static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
