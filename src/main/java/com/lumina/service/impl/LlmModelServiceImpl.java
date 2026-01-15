package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.dto.ModelDevDTO;
import com.lumina.entity.LlmModel;
import com.lumina.mapper.LlmModelMapper;
import com.lumina.service.LlmModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModel> implements LlmModelService {

    @Autowired
    private RestClient restClient;

    @Override
    @Transactional
    public void syncModels() {
        Map<String, ModelDevDTO> response = restClient.get()
                .uri("https://models.dev/api.json")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, ModelDevDTO>>() {
                });

        if (response == null || response.isEmpty()) {
            return;
        }

        // Use a map to deduplicate by model name
        List<LlmModel> models = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, ModelDevDTO> provider : response.entrySet()) {
            if (provider.getValue().getModels() != null) {
                for (ModelDevDTO.ModelData modelData : provider.getValue().getModels().values()) {
                    LlmModel model = new LlmModel();
                    model.setModelName(modelData.getId());
                    model.setProvider(provider.getKey());
                    model.setIsReasoning(modelData.getReasoning());
                    model.setIsToolCall(modelData.getTool_call());

                    if (modelData.getCost() != null) {
                        model.setInputPrice(modelData.getCost().getInput());
                        model.setOutputPrice(modelData.getCost().getOutput());
                        model.setCacheReadPrice(modelData.getCost().getCache_read());
                        model.setCacheWritePrice(modelData.getCost().getCache_write());
                    }
                    if (modelData.getLimit() != null) {
                        model.setContextLimit(modelData.getLimit().getContext());
                        model.setOutputLimit(modelData.getLimit().getOutput());
                    }
                    if (modelData.getModalities() != null){
                        model.setInputType(String.join(",", modelData.getModalities().getInput()));
                    }

                    model.setLastUpdatedAt(modelData.getLast_updated());
                    model.setCreatedAt(now);
                    model.setUpdatedAt(now);

                    models.add(model);
                }
            }
        }

        if (!models.isEmpty()) {
            // Delete all existing models
            this.remove(new QueryWrapper<>());
            // Save new models
            this.saveBatch(models);
        }
    }
}
