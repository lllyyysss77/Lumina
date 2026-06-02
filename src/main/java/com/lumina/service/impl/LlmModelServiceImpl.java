package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.dto.ModelDevDTO;
import com.lumina.entity.LlmModel;
import com.lumina.mapper.LlmModelMapper;
import com.lumina.service.HotPathCacheService;
import com.lumina.service.LlmModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModel> implements LlmModelService {

    @Autowired
    private RestClient restClient;

    @Autowired
    private HotPathCacheService hotPathCacheService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public void syncModels() {
        Map<String, ModelDevDTO> response = restClient.get()
                .uri("https://models.dev/api.json")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, ModelDevDTO>>() {
                });

        if (response == null || response.isEmpty()) {
            return;
        }

        // Use a map to deduplicate by (model_name, provider)
        Map<String, LlmModel> modelMap = new java.util.LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, ModelDevDTO> provider : response.entrySet()) {
            if (provider.getValue().getModels() != null) {
                for (ModelDevDTO.ModelData modelData : provider.getValue().getModels().values()) {
                    String dedupeKey = modelData.getId() + "|" + provider.getKey();
                    if (modelMap.containsKey(dedupeKey)) {
                        continue; // skip duplicate
                    }
                    LlmModel model = new LlmModel();
                    model.setModelName(modelData.getId());
                    model.setProvider(provider.getKey());
                    model.setDisplayName(modelData.getName());
                    model.setFamily(modelData.getFamily());
                    model.setIsReasoning(modelData.getReasoning());
                    model.setIsToolCall(modelData.getTool_call());
                    model.setIsAttachment(modelData.getAttachment());
                    model.setIsStructuredOutput(modelData.getStructured_output());
                    model.setIsTemperature(modelData.getTemperature());
                    model.setIsOpenWeights(modelData.getOpen_weights());
                    model.setKnowledgeCutoff(modelData.getKnowledge());
                    model.setReleaseDate(modelData.getRelease_date());

                    if (modelData.getCost() != null) {
                        model.setInputPrice(modelData.getCost().getInput());
                        model.setOutputPrice(modelData.getCost().getOutput());
                        model.setCacheReadPrice(modelData.getCost().getCache_read());
                        model.setCacheWritePrice(modelData.getCost().getCache_write());
                    }
                    if (modelData.getLimit() != null) {
                        model.setContextLimit(modelData.getLimit().getContext());
                        model.setOutputLimit(modelData.getLimit().getOutput());
                        model.setInputLimit(modelData.getLimit().getInput());
                    }
                    if (modelData.getModalities() != null) {
                        if (modelData.getModalities().getInput() != null) {
                            model.setInputType(String.join(",", modelData.getModalities().getInput()));
                        }
                        if (modelData.getModalities().getOutput() != null) {
                            model.setOutputType(String.join(",", modelData.getModalities().getOutput()));
                        }
                    }

                    model.setLastUpdatedAt(modelData.getLast_updated());
                    model.setCreatedAt(now);
                    model.setUpdatedAt(now);

                    modelMap.put(dedupeKey, model);
                }
            }
        }

        List<LlmModel> models = new ArrayList<>(modelMap.values());

        if (!models.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                // Get existing model_name+provider combinations to determine which are new
                Set<String> existingKeys = this.list(new LambdaQueryWrapper<LlmModel>()
                        .select(LlmModel::getModelName, LlmModel::getProvider))
                        .stream()
                        .map(m -> m.getModelName() + "|" + m.getProvider())
                        .collect(Collectors.toSet());

                List<LlmModel> toInsert = new ArrayList<>();
                List<LlmModel> toUpdate = new ArrayList<>();
                Set<String> seenInBatch = new HashSet<>();

                for (LlmModel m : models) {
                    String key = m.getModelName() + "|" + m.getProvider();
                    if (existingKeys.contains(key)) {
                        // Find existing record and set its id for update
                        LlmModel existing = this.getOne(new LambdaQueryWrapper<LlmModel>()
                                .eq(LlmModel::getModelName, m.getModelName())
                                .eq(LlmModel::getProvider, m.getProvider()));
                        if (existing != null) {
                            m.setId(existing.getId());
                            m.setIsActive(existing.getIsActive()); // preserve active flag
                            m.setCreatedAt(existing.getCreatedAt());
                            toUpdate.add(m);
                        }
                    } else if (seenInBatch.add(key)) {
                        // New record, default is_active = false (user must explicitly select)
                        m.setIsActive(false);
                        toInsert.add(m);
                    }
                }

                if (!toInsert.isEmpty()) {
                    // Re-check existingKeys after dedup to avoid race-condition duplicates
                    Set<String> freshExistingKeys = this.list(new LambdaQueryWrapper<LlmModel>()
                            .select(LlmModel::getModelName, LlmModel::getProvider))
                            .stream()
                            .map(m2 -> m2.getModelName() + "|" + m2.getProvider())
                            .collect(Collectors.toSet());
                    toInsert.removeIf(m -> freshExistingKeys.contains(m.getModelName() + "|" + m.getProvider()));

                    if (!toInsert.isEmpty()) {
                        this.saveBatch(toInsert);
                    }
                    // Auto-activate if this model_name has no active record yet
                    for (LlmModel m : toInsert) {
                        long activeCount = this.count(new LambdaQueryWrapper<LlmModel>()
                                .eq(LlmModel::getModelName, m.getModelName())
                                .eq(LlmModel::getIsActive, true));
                        if (activeCount == 0) {
                            m.setIsActive(true);
                            this.updateById(m);
                        }
                    }
                }
                if (!toUpdate.isEmpty()) {
                    this.updateBatchById(toUpdate);
                }
            });
            hotPathCacheService.invalidateAllModelPrices();
        }
    }

    @Override
    public Page<LlmModel> queryPage(Page<Object> page, LambdaQueryWrapper<LlmModel> queryWrapper) {
        return baseMapper.queryPage(page, queryWrapper);
    }

    @Override
    public LlmModel findLatestByModelName(String modelName) {
        return hotPathCacheService.getModelPrice(modelName, () -> this.getOne(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getModelName, modelName)
                .eq(LlmModel::getIsActive, true)
                .last("limit 1")));
    }

    @Override
    public void setActiveProvider(String modelName, String provider) {
        transactionTemplate.executeWithoutResult(status -> {
            // Deactivate all records for this model
            LlmModel deactivate = new LlmModel();
            deactivate.setIsActive(false);
            this.update(deactivate, new LambdaQueryWrapper<LlmModel>()
                    .eq(LlmModel::getModelName, modelName));

            // Activate the selected provider
            LlmModel activate = new LlmModel();
            activate.setIsActive(true);
            this.update(activate, new LambdaQueryWrapper<LlmModel>()
                    .eq(LlmModel::getModelName, modelName)
                    .eq(LlmModel::getProvider, provider));
        });
        hotPathCacheService.invalidateModelPrice(modelName);
    }

    @Override
    public List<LlmModel> findProvidersByModelName(String modelName) {
        return this.list(new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getModelName, modelName)
                .orderByDesc(LlmModel::getIsActive));
    }

    @Override
    public boolean save(LlmModel entity) {
        boolean saved = super.save(entity);
        if (saved) {
            hotPathCacheService.invalidateModelPrice(entity.getModelName());
        }
        return saved;
    }

    @Override
    public boolean updateById(LlmModel entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            hotPathCacheService.invalidateModelPrice(entity.getModelName());
        }
        return updated;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean removed = super.removeById(id);
        if (removed && id instanceof String modelName) {
            hotPathCacheService.invalidateModelPrice(modelName);
        }
        return removed;
    }
}
