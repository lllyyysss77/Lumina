package com.lumina.util;

import com.lumina.config.LuminaProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtUtil {

    @Autowired
    private LuminaProperties luminaProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(luminaProperties.getAuth().getJwt().getSecret().getBytes());
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + luminaProperties.getAuth().getJwt().getExpiration());

        String token = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();

        // 将 token 存储到 Redis 中，用于后续的注销功能
        redisTemplate.opsForValue().set(
                "jwt:token:" + username,
                token,
                luminaProperties.getAuth().getJwt().getExpiration(),
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
            String redisToken = redisTemplate.opsForValue().get("jwt:token:" + username);
            if (redisToken == null || !redisToken.equals(token)) {
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
            redisTemplate.delete("jwt:token:" + username);
            log.info("Token revoked for user: {}", username);
        } catch (Exception e) {
            log.error("Error revoking token: {}", e.getMessage());
        }
    }

    /**
     * 刷新 token
     */
    public String refreshToken(String token) {
        try {
            String username = getUsernameFromToken(token);
            if (!isTokenExpired(token)) {
                revokeToken(token);
                return generateToken(username);
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