package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.lumina.entity.Provider;
import com.lumina.mapper.ProviderMapper;
import com.lumina.service.HotPathCacheService;
import com.lumina.service.ProviderService;
import com.lumina.service.ProviderWebClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.Serializable;
import java.util.List;

@Service
public class ProviderServiceImpl extends ServiceImpl<ProviderMapper, Provider> implements ProviderService {

    @Autowired
    private RestClient restClient;

    @Autowired
    private HotPathCacheService hotPathCacheService;

    @Autowired
    private ProviderWebClientFactory providerWebClientFactory;

    @Override
    public List<String> getModels(Provider provider) {
        // 对接接口
        String url = provider.getBaseUrl() + "/v1/models";
        JsonNode res = restClient.get()
                .uri(url)
                .header("Authorization", provider.getApiKey())
                .retrieve()
                .body(JsonNode.class);
        List<String> models = res.findValuesAsText("id");
        if (models != null && !models.isEmpty()){
            return models.stream()
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean save(Provider entity) {
        boolean saved = super.save(entity);
        if (saved) {
            invalidateRoutingCaches();
        }
        return saved;
    }

    @Override
    public boolean updateById(Provider entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            invalidateRoutingCaches();
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean removed = super.removeById(id);
        if (removed) {
            invalidateRoutingCaches();
        }
        return removed;
    }

    private void invalidateRoutingCaches() {
        hotPathCacheService.invalidateAllGroupConfigs();
        providerWebClientFactory.invalidateAll();
    }
}
