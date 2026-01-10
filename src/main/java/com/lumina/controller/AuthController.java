package com.lumina.controller;

import com.lumina.config.LuminaProperties;
import com.lumina.dto.ApiResponse;
import com.lumina.dto.LoginRequest;
import com.lumina.dto.LoginResponse;
import com.lumina.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LuminaProperties luminaProperties;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String token = jwtUtil.generateToken(loginRequest.getUsername());

            LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .expiresIn(luminaProperties.getAuth().getJwt().getExpiration())
                .username(loginRequest.getUsername())
                .build();

            log.info("User logged in successfully: {}", loginRequest.getUsername());

            return ApiResponse.success("登录成功", loginResponse);

        } catch (Exception e) {
            log.error("Login failed for user: {}", loginRequest.getUsername(), e);
            return ApiResponse.error(401, "用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = jwtUtil.getTokenFromHeader(authHeader);
            if (token != null) {
                jwtUtil.revokeToken(token);
                SecurityContextHolder.clearContext();
                log.info("User logged out successfully");
                return ApiResponse.success("登出成功", null);
            }
            return ApiResponse.error("无效的令牌");
        } catch (Exception e) {
            log.error("Logout failed", e);
            return ApiResponse.error("登出失败");
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String oldToken = jwtUtil.getTokenFromHeader(authHeader);
            if (oldToken == null) {
                return ApiResponse.error("无效的令牌");
            }

            String newToken = jwtUtil.refreshToken(oldToken);
            if (newToken == null) {
                return ApiResponse.error("令牌刷新失败");
            }

            String username = jwtUtil.getUsernameFromToken(newToken);

            LoginResponse loginResponse = LoginResponse.builder()
                .token(newToken)
                .type("Bearer")
                .expiresIn(luminaProperties.getAuth().getJwt().getExpiration())
                .username(username)
                .build();

            log.info("Token refreshed successfully for user: {}", username);

            return ApiResponse.success("令牌刷新成功", loginResponse);

        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ApiResponse.error("令牌刷新失败");
        }
    }

    @GetMapping("/me")
    public ApiResponse<String> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return ApiResponse.success("获取当前用户成功", authentication.getName());
        }
        return ApiResponse.error("未认证");
    }
}