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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

        LocalDateTime now = LocalDateTime.now();
        List<LlmModel> models = buildModels(response, now);

        if (!models.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                Map<String, LlmModel> existingByKey = this.list(new LambdaQueryWrapper<LlmModel>()
                                .select(LlmModel::getId, LlmModel::getModelName, LlmModel::getProvider,
                                        LlmModel::getIsActive, LlmModel::getCreatedAt))
                        .stream()
                        .collect(Collectors.toMap(
                                m -> uniqueKey(m.getModelName(), m.getProvider()),
                                Function.identity(),
                                (first, ignored) -> first
                        ));

                List<LlmModel> toInsert = new ArrayList<>();
                List<LlmModel> toUpdate = new ArrayList<>();
                Set<String> seenInBatch = new HashSet<>();

                for (LlmModel m : models) {
                    String key = uniqueKey(m.getModelName(), m.getProvider());
                    LlmModel existing = existingByKey.get(key);
                    if (existing != null) {
                        m.setId(existing.getId());
                        m.setIsActive(existing.getIsActive()); // preserve active flag
                        m.setCreatedAt(existing.getCreatedAt());
                        toUpdate.add(m);
                    } else if (seenInBatch.add(key)) {
                        // New record, default is_active = false (user must explicitly select)
                        m.setIsActive(false);
                        toInsert.add(m);
                    }
                }

                if (!toInsert.isEmpty()) {
                    Map<String, LlmModel> freshExistingByKey = this.list(new LambdaQueryWrapper<LlmModel>()
                                    .select(LlmModel::getModelName, LlmModel::getProvider))
                            .stream()
                            .collect(Collectors.toMap(
                                    m -> uniqueKey(m.getModelName(), m.getProvider()),
                                    Function.identity(),
                                    (first, ignored) -> first
                            ));
                    toInsert.removeIf(m -> freshExistingByKey.containsKey(uniqueKey(m.getModelName(), m.getProvider())));

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

    static List<LlmModel> buildModels(Map<String, ModelDevDTO> response, LocalDateTime now) {
        Map<String, LlmModel> modelMap = new LinkedHashMap<>();
        for (Map.Entry<String, ModelDevDTO> provider : response.entrySet()) {
            if (provider.getValue() != null && provider.getValue().getModels() != null) {
                for (ModelDevDTO.ModelData modelData : provider.getValue().getModels().values()) {
                    if (modelData == null || !hasText(modelData.getId()) || !hasText(provider.getKey())) {
                        continue;
                    }
                    String dedupeKey = uniqueKey(modelData.getId(), provider.getKey());
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

        return new ArrayList<>(modelMap.values());
    }

    private static String uniqueKey(String modelName, String provider) {
        return normalizeUniqueKeyPart(modelName) + "|" + normalizeUniqueKeyPart(provider);
    }

    private static String normalizeUniqueKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
