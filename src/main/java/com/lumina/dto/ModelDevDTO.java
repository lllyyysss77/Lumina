package com.lumina.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelDevDTO {
    private String id;
    private String name;
    private Map<String, ModelData> models;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelData {
        private String id;
        private String name;
        private String family;
        private Boolean reasoning;
        private Boolean tool_call;
        private CostData cost;
        private ModelLimit limit;
        private Modalities modalities;
        private String last_updated;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CostData {
        private BigDecimal input;
        private BigDecimal output;
        private BigDecimal cache_read;
        private BigDecimal cache_write;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelLimit {
        private Integer context;
        private Integer output;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Modalities {
        private List<String> input;
    }
}
