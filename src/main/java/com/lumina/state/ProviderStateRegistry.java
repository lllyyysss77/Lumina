package com.lumina.state;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderStateRegistry {

    private final ConcurrentHashMap<String, ProviderRuntimeState> stateMap = new ConcurrentHashMap<>();

    public ProviderRuntimeState get(String providerId, int initialWeight) {
        return stateMap.computeIfAbsent(
                providerId,
                k -> new ProviderRuntimeState(providerId, initialWeight)
        );
    }

    public void clear() {
        stateMap.clear();
    }
}
