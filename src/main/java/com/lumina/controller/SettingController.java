package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumina.entity.Setting;
import com.lumina.service.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<Setting>> getAllSettings() {
        List<Setting> settings = settingService.list();
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/{key}")
    public ResponseEntity<Setting> getSettingByKey(@PathVariable String key) {
        Setting setting = settingService.getById(key);
        if (setting == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(setting);
    }

    @PostMapping
    public ResponseEntity<Setting> createSetting(@RequestBody Setting setting) {
        setting.setCreatedAt(LocalDateTime.now());
        setting.setUpdatedAt(LocalDateTime.now());
        boolean success = settingService.save(setting);
        return success ? ResponseEntity.ok(setting) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/{key}")
    public ResponseEntity<Setting> updateSetting(@PathVariable String key, @RequestBody Setting setting) {
        setting.setSettingKey(key);
        setting.setUpdatedAt(LocalDateTime.now());
        boolean success = settingService.updateById(setting);
        return success ? ResponseEntity.ok(setting) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteSetting(@PathVariable String key) {
        boolean success = settingService.removeById(key);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, String>> getSettingsAsMap() {
        List<Setting> settings = settingService.list();
        Map<String, String> settingsMap = settings.stream()
                .collect(Collectors.toMap(Setting::getSettingKey, Setting::getSettingValue));
        return ResponseEntity.ok(settingsMap);
    }

    @PostMapping("/batch")
    public ResponseEntity<Void> batchUpdateSettings(@RequestBody Map<String, String> settingsMap) {
        settingsMap.forEach((key, value) -> {
            Setting setting = new Setting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
            settingService.saveOrUpdate(setting);
        });
        return ResponseEntity.ok().build();
    }
}
