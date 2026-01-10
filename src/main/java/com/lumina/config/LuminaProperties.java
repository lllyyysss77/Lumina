package com.lumina.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lumina")
public class LuminaProperties {

    /**
     * 代理配置
     */
    private Proxy proxy = new Proxy();

    /**
     * 统计配置
     */
    private Stats stats = new Stats();

    /**
     * 模型同步配置
     */
    private Sync sync = new Sync();

    /**
     * 限流配置
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 认证配置
     */
    private Auth auth = new Auth();

    /**
     * API配置
     */
    private Api api = new Api();

    @Data
    public static class Proxy {
        private String url;
        private boolean enabled = false;
        private int timeout = 30000;
    }

    @Data
    public static class Stats {
        private int saveInterval = 5; // 分钟
        private int logKeepDays = 7;
    }

    @Data
    public static class Sync {
        private int llmInterval = 24; // 小时
        private int modelInfoInterval = 24; // 小时
    }

    @Data
    public static class RateLimit {
        private boolean enabled = false;
        private int requestsPerMinute = 1000;
    }

    @Data
    public static class Auth {
        private Jwt jwt = new Jwt();

        @Data
        public static class Jwt {
            private String secret;
            private long expiration = 86400000; // 24小时，毫秒
            private long refreshExpiration = 604800000; // 7天，毫秒
        }
    }

    @Data
    public static class Api {
        private String version = "v1";
        private String prefix = "/api/v1";
    }
}