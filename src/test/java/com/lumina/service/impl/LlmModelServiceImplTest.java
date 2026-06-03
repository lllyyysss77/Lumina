package com.lumina.service.impl;

import com.lumina.dto.ModelDevDTO;
import com.lumina.entity.LlmModel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmModelServiceImplTest {

    @Test
    void buildModelsDeduplicatesByDatabaseUniqueKeySemantics() {
        ModelDevDTO provider = new ModelDevDTO();
        Map<String, ModelDevDTO.ModelData> upstreamModels = new LinkedHashMap<>();
        upstreamModels.put("first", model("sao10K/l3-8b-lunaris"));
        upstreamModels.put("duplicateCase", model("sao10k/l3-8b-lunaris"));
        provider.setModels(upstreamModels);

        List<LlmModel> models = LlmModelServiceImpl.buildModels(
                Map.of("novita-ai", provider),
                LocalDateTime.of(2026, 6, 3, 12, 0)
        );

        assertEquals(1, models.size());
        assertEquals("sao10K/l3-8b-lunaris", models.get(0).getModelName());
        assertEquals("novita-ai", models.get(0).getProvider());
    }

    @Test
    void buildModelsSkipsBlankUniqueKeyParts() {
        ModelDevDTO provider = new ModelDevDTO();
        Map<String, ModelDevDTO.ModelData> upstreamModels = new LinkedHashMap<>();
        upstreamModels.put("blank", model(" "));
        upstreamModels.put("valid", model("gpt-4o"));
        provider.setModels(upstreamModels);

        List<LlmModel> models = LlmModelServiceImpl.buildModels(
                Map.of("openai", provider),
                LocalDateTime.of(2026, 6, 3, 12, 0)
        );

        assertEquals(1, models.size());
        assertEquals("gpt-4o", models.get(0).getModelName());
    }

    private ModelDevDTO.ModelData model(String id) {
        ModelDevDTO.ModelData model = new ModelDevDTO.ModelData();
        model.setId(id);
        model.setName(id);
        return model;
    }
}
