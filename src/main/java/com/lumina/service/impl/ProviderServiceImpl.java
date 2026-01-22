package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.lumina.entity.Provider;
import com.lumina.mapper.ProviderMapper;
import com.lumina.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProviderServiceImpl extends ServiceImpl<ProviderMapper, Provider> implements ProviderService {

    @Autowired
    private RestClient restClient;

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
}
