package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.ChannelKey;
import com.lumina.service.ChannelKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/channel-keys")
public class ChannelKeyController {

    @Autowired
    private ChannelKeyService channelKeyService;

    @GetMapping
    public ApiResponse<List<ChannelKey>> getAllChannelKeys() {
        List<ChannelKey> channelKeys = channelKeyService.list();
        return ApiResponse.success(channelKeys);
    }

    @GetMapping("/{id}")
    public ApiResponse<ChannelKey> getChannelKeyById(@PathVariable Long id) {
        ChannelKey channelKey = channelKeyService.getById(id);
        if (channelKey == null) {
            throw new IllegalArgumentException("ChannelKey not found with id: " + id);
        }
        return ApiResponse.success(channelKey);
    }

    @PostMapping
    public ApiResponse<ChannelKey> createChannelKey(@RequestBody ChannelKey channelKey) {
        channelKey.setCreatedAt(LocalDateTime.now());
        channelKey.setUpdatedAt(LocalDateTime.now());
        boolean success = channelKeyService.save(channelKey);
        if (!success) {
            throw new IllegalArgumentException("Failed to create channel key");
        }
        return ApiResponse.success(channelKey);
    }

    @PutMapping("/{id}")
    public ApiResponse<ChannelKey> updateChannelKey(@PathVariable Long id, @RequestBody ChannelKey channelKey) {
        channelKey.setId(id);
        channelKey.setUpdatedAt(LocalDateTime.now());
        boolean success = channelKeyService.updateById(channelKey);
        if (!success) {
            throw new IllegalArgumentException("ChannelKey not found with id: " + id);
        }
        return ApiResponse.success(channelKey);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteChannelKey(@PathVariable Long id) {
        boolean success = channelKeyService.removeById(id);
        if (!success) {
            throw new IllegalArgumentException("ChannelKey not found with id: " + id);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<ChannelKey>> getChannelKeysByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<ChannelKey> page = channelKeyService.page(new Page<>(current, size));
        return ApiResponse.success(page);
    }

    @GetMapping("/channel/{channelId}")
    public ApiResponse<List<ChannelKey>> getChannelKeysByChannelId(@PathVariable Long channelId) {
        QueryWrapper<ChannelKey> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId);
        List<ChannelKey> channelKeys = channelKeyService.list(wrapper);
        return ApiResponse.success(channelKeys);
    }

    @GetMapping("/enabled")
    public ApiResponse<List<ChannelKey>> getEnabledChannelKeys() {
        QueryWrapper<ChannelKey> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        List<ChannelKey> channelKeys = channelKeyService.list(wrapper);
        return ApiResponse.success(channelKeys);
    }
}
