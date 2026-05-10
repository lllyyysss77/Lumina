package com.lumina.util;

import com.lumina.config.LuminaProperties;
import com.lumina.dto.LoginResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtUtil {

    @Autowired
    private LuminaProperties luminaProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private volatile SecretKey signingKey;

    /**
     * 在应用启动时就把签名密钥初始化好，这样密钥缺失 / 过短这类
     * 配置错误会直接让 Spring 启动失败，而不是等第一次带 token 的
     * 请求进来才抛 500。
     */
    @PostConstruct
    void init() {
        getSigningKey();
        log.info("JWT signing key initialized.");
    }

    private SecretKey getSigningKey() {
        if (signingKey == null) {
            synchronized (this) {
                if (signingKey == null) {
                    // 使用配置的密钥字符串生成一个符合HS512要求的安全密钥
                    String secret = luminaProperties.getAuth().getJwt().getSecret();

                    if (secret == null || secret.isBlank()) {
                        throw new IllegalStateException(
                                "JWT secret is not configured. Set the LUMINA_JWT_SECRET environment variable "
                                        + "(or lumina.auth.jwt.secret) to a strong random value before starting the application.");
                    }
                    if (secret.length() < 32) {
                        throw new IllegalStateException(
                                "JWT secret is too short (" + secret.length() + " chars). "
                                        + "Use at least 32 characters of high-entropy random data.");
                    }

                    // 使用SHA-256哈希处理配置的密钥，然后扩展到512位(64字节)以满足HS512要求
                    try {
                        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                        byte[] keyBytes = sha256.digest(secret.getBytes());
                        
                        // 创建一个64字节的数组来满足HS512的要求
                        byte[] expandedKey = new byte[64];
                        System.arraycopy(keyBytes, 0, expandedKey, 0, Math.min(keyBytes.length, 64));
                        
                        signingKey = Keys.hmacShaKeyFor(expandedKey);
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException("Failed to generate signing key", e);
                    }
                }
            }
        }
        return signingKey;
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(String username) {
        return generateToken(username, luminaProperties.getAuth().getJwt().getExpiration());
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(String username) {
        return generateToken(username, luminaProperties.getAuth().getJwt().getRefreshExpiration());
    }

    private String generateToken(String username, long expirationMs) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        // 将 token 存储到 Redis 中，用于后续的注销功能
        redisTemplate.opsForValue().set(
                "jwt:token:" + username + ":" + token,
                "valid",
                expirationMs,
                TimeUnit.MILLISECONDS
        );

        return token;
    }

    /**
     * 从 token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    /**
     * 验证 token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            String username = getUsernameFromToken(token);

            // 检查 token 是否存在于 Redis 中（用于注销验证）
            String redisTokenStatus = redisTemplate.opsForValue().get("jwt:token:" + username + ":" + token);
            if (redisTokenStatus == null || !redisTokenStatus.equals("valid")) {
                return false;
            }

            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 token 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 注销 token
     */
    public void revokeToken(String token) {
        try {
            String username = getUsernameFromToken(token);
            redisTemplate.delete("jwt:token:" + username + ":" + token);
            log.info("Token revoked for user: {}", username);
        } catch (Exception e) {
            log.error("Error revoking token: {}", e.getMessage());
        }
    }

    /**
     * 刷新 token
     */
    public LoginResponse refreshToken(String refreshToken) {
        try {
            if (validateToken(refreshToken)) {
                String username = getUsernameFromToken(refreshToken);
                // 刷新时，生成一对新的 token 和 refresh token
                String newToken = generateToken(username);
                String newRefreshToken = generateRefreshToken(username);
                
                // 可选：注销旧的 refresh token
                revokeToken(refreshToken);
                
                return LoginResponse.builder()
                    .token(newToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn(luminaProperties.getAuth().getJwt().getExpiration())
                    .username(username)
                    .build();
            }
        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从请求头中获取 token
     */
    public String getTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}