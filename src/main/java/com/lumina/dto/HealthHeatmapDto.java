package com.lumina.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthHeatmapDto {
    private double overallSuccessRate;
    private int days;
    private List<HeatmapCell> cells;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapCell {
        private long timestamp;
        private long totalRequests;
        private long successRequests;
        private double successRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapBucket {
        private String bucketStart;
        private Long totalRequests;
        private Long successRequests;
    }
}
