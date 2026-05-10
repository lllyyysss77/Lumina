package com.lumina.controller;

import com.lumina.dto.ApiResponse;
import com.lumina.entity.Setting;
import com.lumina.service.SettingService;
import com.lumina.service.SystemFlagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingController {

    @Autowired
    private SettingService settingService;

    @Autowired
    private SystemFlagService systemFlagService;

    @GetMapping
    public ApiResponse<List<Setting>> getAllSettings() {
        List<Setting> settings = settingService.list();
        return ApiResponse.success(settings);
    }

    @GetMapping("/{key}")
    public ApiResponse<Setting> getSettingByKey(@PathVariable String key) {
        Setting setting = settingService.getById(key);
        if (setting == null) {
            throw new IllegalArgumentException("Setting not found with key: " + key);
        }
        return ApiResponse.success(setting);
    }

    @PostMapping
    public ApiResponse<Setting> createSetting(@RequestBody Setting setting) {
        setting.setCreatedAt(LocalDateTime.now());
        setting.setUpdatedAt(LocalDateTime.now());
        boolean success = settingService.save(setting);
        if (!success) {
            throw new IllegalArgumentException("Failed to create setting");
        }
        systemFlagService.invalidate();
        return ApiResponse.success(setting);
    }

    @PutMapping("/{key}")
    public ApiResponse<Setting> updateSetting(@PathVariable String key, @RequestBody Setting setting) {
        setting.setSettingKey(key);
        setting.setUpdatedAt(LocalDateTime.now());
        boolean success = settingService.updateById(setting);
        if (!success) {
            throw new IllegalArgumentException("Setting not found with key: " + key);
        }
        systemFlagService.invalidate();
        return ApiResponse.success(setting);
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> deleteSetting(@PathVariable String key) {
        boolean success = settingService.removeById(key);
        if (!success) {
            throw new IllegalArgumentException("Setting not found with key: " + key);
        }
        systemFlagService.invalidate();
        return ApiResponse.success(null);
    }

    @GetMapping("/map")
    public ApiResponse<Map<String, String>> getSettingsAsMap() {
        List<Setting> settings = settingService.list();
        Map<String, String> settingsMap = settings.stream()
                .collect(Collectors.toMap(Setting::getSettingKey, Setting::getSettingValue));
        return ApiResponse.success(settingsMap);
    }

    @PostMapping("/batch")
    public ApiResponse<Void> batchUpdateSettings(@RequestBody Map<String, String> settingsMap) {
        settingsMap.forEach((key, value) -> {
            Setting setting = new Setting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
            settingService.saveOrUpdate(setting);
        });
        systemFlagService.invalidate();
        return ApiResponse.success(null);
    }

    // ---------- 自用模式（Self-use mode） ----------
    // 开启后 /v1/** 与 /v1beta/** 将跳过 API Key 校验，仅建议在完全受信任的
    // 本地 / 内网环境下使用。

    @GetMapping("/self-use-mode")
    public ApiResponse<Map<String, Boolean>> getSelfUseMode() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("enabled", systemFlagService.isSelfUseModeEnabled());
        return ApiResponse.success(result);
    }

    @PutMapping("/self-use-mode")
    public ApiResponse<Map<String, Boolean>> updateSelfUseMode(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("enabled");
        boolean enabled;
        if (raw instanceof Boolean b) {
            enabled = b;
        } else if (raw instanceof String s) {
            enabled = Boolean.parseBoolean(s.trim());
        } else {
            throw new IllegalArgumentException("Field 'enabled' must be a boolean");
        }
        systemFlagService.setSelfUseModeEnabled(enabled);
        Map<String, Boolean> result = new HashMap<>();
        result.put("enabled", enabled);
        return ApiResponse.success(result);
    }
}
