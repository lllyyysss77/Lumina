package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.ChannelKey;
import com.lumina.service.ChannelKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/channel-keys")
public class ChannelKeyController {

    @Autowired
    private ChannelKeyService channelKeyService;

    @GetMapping
    public ResponseEntity<List<ChannelKey>> getAllChannelKeys() {
        List<ChannelKey> channelKeys = channelKeyService.list();
        return ResponseEntity.ok(channelKeys);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChannelKey> getChannelKeyById(@PathVariable Long id) {
        ChannelKey channelKey = channelKeyService.getById(id);
        if (channelKey == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(channelKey);
    }

    @PostMapping
    public ResponseEntity<ChannelKey> createChannelKey(@RequestBody ChannelKey channelKey) {
        channelKey.setCreatedAt(LocalDateTime.now());
        channelKey.setUpdatedAt(LocalDateTime.now());
        boolean success = channelKeyService.save(channelKey);
        return success ? ResponseEntity.ok(channelKey) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChannelKey> updateChannelKey(@PathVariable Long id, @RequestBody ChannelKey channelKey) {
        channelKey.setId(id);
        channelKey.setUpdatedAt(LocalDateTime.now());
        boolean success = channelKeyService.updateById(channelKey);
        return success ? ResponseEntity.ok(channelKey) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannelKey(@PathVariable Long id) {
        boolean success = channelKeyService.removeById(id);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/page")
    public ResponseEntity<Page<ChannelKey>> getChannelKeysByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<ChannelKey> page = channelKeyService.page(new Page<>(current, size));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<ChannelKey>> getChannelKeysByChannelId(@PathVariable Long channelId) {
        QueryWrapper<ChannelKey> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId);
        List<ChannelKey> channelKeys = channelKeyService.list(wrapper);
        return ResponseEntity.ok(channelKeys);
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<ChannelKey>> getEnabledChannelKeys() {
        QueryWrapper<ChannelKey> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        List<ChannelKey> channelKeys = channelKeyService.list(wrapper);
        return ResponseEntity.ok(channelKeys);
    }
}
