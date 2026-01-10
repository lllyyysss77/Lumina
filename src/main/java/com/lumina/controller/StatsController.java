package com.lumina.controller;

import com.lumina.dto.ApiResponse;
import com.lumina.entity.*;
import com.lumina.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    @Autowired
    private StatsTotalService statsTotalService;
    @Autowired
    private StatsHourlyService statsHourlyService;
    @Autowired
    private StatsDailyService statsDailyService;
    @Autowired
    private StatsModelService statsModelService;
    @Autowired
    private StatsChannelService statsChannelService;
    @Autowired
    private StatsApiKeyService statsApiKeyService;

    @GetMapping("/total")
    public ApiResponse<StatsTotal> getTotalStats() {
        StatsTotal stats = statsTotalService.getById(1);
        return ApiResponse.success(stats);
    }

    @GetMapping("/hourly")
    public ApiResponse<List<StatsHourly>> getHourlyStats(@RequestParam String date) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("stat_date", date);
        List<StatsHourly> stats = statsHourlyService.listByMap(paramMap);
        return ApiResponse.success(stats);
    }

    @GetMapping("/daily")
    public ApiResponse<List<StatsDaily>> getDailyStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (startDate != null && endDate != null) {
            throw new IllegalArgumentException("Date range query not implemented");
        }
        List<StatsDaily> stats = statsDailyService.list();
        return ApiResponse.success(stats);
    }

    @GetMapping("/model/{modelName}")
    public ApiResponse<List<StatsModel>> getModelStats(@PathVariable String modelName) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("model_name", modelName);
        List<StatsModel> stats = statsModelService.listByMap(paramMap);
        return ApiResponse.success(stats);
    }

    @GetMapping("/channel/{channelId}")
    public ApiResponse<StatsChannel> getChannelStats(@PathVariable Long channelId) {
        StatsChannel stats = statsChannelService.getById(channelId);
        if (stats == null) {
            throw new IllegalArgumentException("StatsChannel not found with id: " + channelId);
        }
        return ApiResponse.success(stats);
    }

    @GetMapping("/apikey/{apiKeyId}")
    public ApiResponse<StatsApiKey> getApiKeyStats(@PathVariable Long apiKeyId) {
        StatsApiKey stats = statsApiKeyService.getById(apiKeyId);
        if (stats == null) {
            throw new IllegalArgumentException("StatsApiKey not found with id: " + apiKeyId);
        }
        return ApiResponse.success(stats);
    }

    @GetMapping("/all-models")
    public ApiResponse<List<StatsModel>> getAllModelStats() {
        List<StatsModel> stats = statsModelService.list();
        return ApiResponse.success(stats);
    }

    @GetMapping("/all-channels")
    public ApiResponse<List<StatsChannel>> getAllChannelStats() {
        List<StatsChannel> stats = statsChannelService.list();
        return ApiResponse.success(stats);
    }

    @GetMapping("/all-apikeys")
    public ApiResponse<List<StatsApiKey>> getAllApiKeyStats() {
        List<StatsApiKey> stats = statsApiKeyService.list();
        return ApiResponse.success(stats);
    }
}
