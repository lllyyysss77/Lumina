package com.lumina.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumina.entity.ApiKey;
import reactor.core.publisher.Mono;

public interface ApiKeyService extends IService<ApiKey> {
    ApiKey generateApiKey(String name);

    Mono<Boolean> validateApiKey(String apiKey);
}
