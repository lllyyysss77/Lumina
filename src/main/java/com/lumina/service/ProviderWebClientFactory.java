package com.lumina.service;

import com.lumina.dto.ModelGroupConfigItem;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderWebClientFactory {

    private static final int MAX_CLIENTS = 512;

    private final WebClient.Builder webClientBuilder;
    private final ConcurrentHashMap<String, WebClient> clients = new ConcurrentHashMap<>();

    public ProviderWebClientFactory(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public WebClient getClient(ModelGroupConfigItem provider) {
        String authHeader = toAuthHeader(provider.getApiKey());
        String key = provider.getBaseUrl() + "|" + authHeader;

        if (clients.size() >= MAX_CLIENTS && !clients.containsKey(key)) {
            clients.clear();
        }

        return clients.computeIfAbsent(key, ignored -> {
            WebClient.Builder builder = webClientBuilder.clone()
                    .baseUrl(provider.getBaseUrl());
            if (StringUtils.hasText(authHeader)) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, authHeader);
            }
            return builder.build();
        });
    }

    public void invalidateAll() {
        clients.clear();
    }

    private String toAuthHeader(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        return apiKey.startsWith("Bearer ") ? apiKey : "Bearer " + apiKey;
    }
}
