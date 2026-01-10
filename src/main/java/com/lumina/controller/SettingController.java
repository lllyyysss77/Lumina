package com.lumina.controller;

import com.lumina.dto.ApiResponse;
import com.lumina.entity.Setting;
import com.lumina.service.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingController {

    @Autowired
    private SettingService settingService;

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
        return ApiResponse.success(setting);
    }

    @DeleteMapping("/{key}")
    public ApiResponse<Void> deleteSetting(@PathVariable String key) {
        boolean success = settingService.removeById(key);
        if (!success) {
            throw new IllegalArgumentException("Setting not found with key: " + key);
        }
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
        return ApiResponse.success(null);
    }
}
