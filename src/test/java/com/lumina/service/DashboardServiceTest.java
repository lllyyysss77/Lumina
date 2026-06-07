package com.lumina.service;

import com.lumina.dto.HealthHeatmapDto;
import com.lumina.mapper.DashboardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardMapper dashboardMapper;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService();
        ReflectionTestUtils.setField(dashboardService, "dashboardMapper", dashboardMapper);
        ReflectionTestUtils.setField(dashboardService, "clock",
                Clock.fixed(Instant.parse("2026-06-07T02:37:42Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void healthHeatmapUsesRealQuarterHourBuckets() {
        when(dashboardMapper.getHealthHeatmapBuckets("2026-06-06 10:30:00", "2026-06-07 10:37:43"))
                .thenReturn(List.of(
                        HealthHeatmapDto.HeatmapBucket.builder()
                                .bucketStart("2026-06-07 10:00:00")
                                .totalRequests(28L)
                                .successRequests(25L)
                                .build(),
                        HealthHeatmapDto.HeatmapBucket.builder()
                                .bucketStart("2026-06-07 10:15:00")
                                .totalRequests(338L)
                                .successRequests(332L)
                                .build()
                ));

        HealthHeatmapDto heatmap = dashboardService.getHealthHeatmap(1);

        assertEquals(97, heatmap.getCells().size());

        HealthHeatmapDto.HeatmapCell tenOClock = heatmap.getCells().get(94);
        assertEquals(28, tenOClock.getTotalRequests());
        assertEquals(25, tenOClock.getSuccessRequests());
        assertEquals(25 * 100.0 / 28, tenOClock.getSuccessRate(), 0.000001);

        HealthHeatmapDto.HeatmapCell tenFifteen = heatmap.getCells().get(95);
        assertEquals(338, tenFifteen.getTotalRequests());
        assertEquals(332, tenFifteen.getSuccessRequests());
        assertEquals(332 * 100.0 / 338, tenFifteen.getSuccessRate(), 0.000001);

        HealthHeatmapDto.HeatmapCell emptyBucket = heatmap.getCells().get(96);
        assertEquals(0, emptyBucket.getTotalRequests());
        assertEquals(0, emptyBucket.getSuccessRequests());
        assertEquals(-1, emptyBucket.getSuccessRate());

        assertEquals((25 + 332) * 100.0 / (28 + 338), heatmap.getOverallSuccessRate(), 0.000001);
    }
}
