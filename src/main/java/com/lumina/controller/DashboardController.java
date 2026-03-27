package com.lumina.controller;

import com.lumina.dto.ApiResponse;
import com.lumina.dto.DashboardObservabilityDto;
import com.lumina.dto.DashboardOverviewDto;
import com.lumina.dto.ModelTokenUsageDto;
import com.lumina.dto.ProviderStatsDto;
import com.lumina.dto.RequestTrafficDto;
import com.lumina.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仪表盘控制器
 * 提供仪表盘统计数据的 API 接口
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 获取仪表盘概览统计
     * 包括：总请求数、预估费用、平均延迟、成功率及其增长率
     *
     * @return 概览统计数据
     */
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewDto> getOverview() {
        DashboardOverviewDto overview = dashboardService.getOverview();
        return ApiResponse.success(overview);
    }

    /**
     * 获取24小时请求流量数据
     * 用于绘制请求流量趋势图
     *
     * @return 按小时统计的请求流量列表
     */
    @GetMapping("/traffic")
    public ApiResponse<List<RequestTrafficDto>> getRequestTraffic() {
        List<RequestTrafficDto> traffic = dashboardService.getRequestTraffic();
        return ApiResponse.success(traffic);
    }

    /**
     * 获取模型 Token 使用统计
     * 显示各模型的 Token 使用量和占比
     *
     * @return 模型 Token 使用统计列表
     */
    @GetMapping("/model-token-usage")
    public ApiResponse<List<ModelTokenUsageDto>> getModelTokenUsage() {
        List<ModelTokenUsageDto> usage = dashboardService.getModelTokenUsage();
        return ApiResponse.success(usage);
    }

    /**
     * 获取供应商统计排名
     * 显示各供应商的调用次数、费用、延迟、成功率等指标
     *
     * @param limit 返回的供应商数量，默认10个
     * @return 供应商统计排名列表
     */
    @GetMapping("/provider-stats")
    public ApiResponse<List<ProviderStatsDto>> getProviderStats(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<ProviderStatsDto> stats = dashboardService.getProviderStats(limit);
        return ApiResponse.success(stats);
    }

    @GetMapping("/observability")
    public ApiResponse<DashboardObservabilityDto> getObservability() {
        return ApiResponse.success(dashboardService.getObservability());
    }
}
