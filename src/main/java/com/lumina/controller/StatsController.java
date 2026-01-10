package com.lumina.controller;

import com.lumina.entity.*;
import com.lumina.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<StatsTotal> getTotalStats() {
        StatsTotal stats = statsTotalService.getById(1);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/hourly")
    public ResponseEntity<List<StatsHourly>> getHourlyStats(@RequestParam String date) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("stat_date", date);
        List<StatsHourly> stats = statsHourlyService.listByMap(paramMap);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/daily")
    public ResponseEntity<List<StatsDaily>> getDailyStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (startDate != null && endDate != null) {
            // Get stats for date range
            return ResponseEntity.notFound().build();
        }
        List<StatsDaily> stats = statsDailyService.list();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/model/{modelName}")
    public ResponseEntity<List<StatsModel>> getModelStats(@PathVariable String modelName) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("model_name", modelName);
        List<StatsModel> stats = statsModelService.listByMap(paramMap);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<StatsChannel> getChannelStats(@PathVariable Long channelId) {
        StatsChannel stats = statsChannelService.getById(channelId);
        return stats != null ? ResponseEntity.ok(stats) : ResponseEntity.notFound().build();
    }

    @GetMapping("/apikey/{apiKeyId}")
    public ResponseEntity<StatsApiKey> getApiKeyStats(@PathVariable Long apiKeyId) {
        StatsApiKey stats = statsApiKeyService.getById(apiKeyId);
        return stats != null ? ResponseEntity.ok(stats) : ResponseEntity.notFound().build();
    }

    @GetMapping("/all-models")
    public ResponseEntity<List<StatsModel>> getAllModelStats() {
        List<StatsModel> stats = statsModelService.list();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/all-channels")
    public ResponseEntity<List<StatsChannel>> getAllChannelStats() {
        List<StatsChannel> stats = statsChannelService.list();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/all-apikeys")
    public ResponseEntity<List<StatsApiKey>> getAllApiKeyStats() {
        List<StatsApiKey> stats = statsApiKeyService.list();
        return ResponseEntity.ok(stats);
    }
}
