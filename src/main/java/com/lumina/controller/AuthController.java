package com.lumina.controller;

import com.lumina.config.LuminaProperties;
import com.lumina.dto.ApiResponse;
import com.lumina.dto.LoginRequest;
import com.lumina.dto.LoginResponse;
import com.lumina.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private ReactiveAuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LuminaProperties luminaProperties;

    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
        )
        .map(authentication -> {
            String token = jwtUtil.generateToken(loginRequest.getUsername());

            LoginResponse loginResponse = LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .expiresIn(luminaProperties.getAuth().getJwt().getExpiration())
                .username(loginRequest.getUsername())
                .build();

            log.info("User logged in successfully: {}", loginRequest.getUsername());
            return ApiResponse.success("登录成功", loginResponse);
        })
        .onErrorResume(e -> {
            log.error("Login failed for user: {}", loginRequest.getUsername());
            return Mono.just(ApiResponse.error(401, "用户名或密码错误"));
        });
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@RequestHeader("Authorization") String authHeader) {
        String token = jwtUtil.getTokenFromHeader(authHeader);
        if (token != null) {
            jwtUtil.revokeToken(token);
            log.info("User logged out successfully");
            return Mono.just(ApiResponse.success("登出成功", null));
        }
        return Mono.just(ApiResponse.error("无效的令牌"));
    }

    @GetMapping("/me")
    public Mono<ApiResponse<String>> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> {
                    if (context.getAuthentication() != null && context.getAuthentication().isAuthenticated()) {
                        return ApiResponse.success("获取当前用户成功", context.getAuthentication().getName());
                    }
                    return ApiResponse.<String>error("未认证");
                })
                .defaultIfEmpty(ApiResponse.error("未认证"));
    }
}
