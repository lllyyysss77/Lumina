package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.Channel;
import com.lumina.service.ChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/channels")
public class ChannelController {

    @Autowired
    private ChannelService channelService;

    @GetMapping
    public ResponseEntity<List<Channel>> getAllChannels() {
        List<Channel> channels = channelService.list();
        return ResponseEntity.ok(channels);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Channel> getChannelById(@PathVariable Long id) {
        Channel channel = channelService.getById(id);
        if (channel == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(channel);
    }

    @PostMapping
    public ResponseEntity<Channel> createChannel(@RequestBody Channel channel) {
        channel.setCreatedAt(LocalDateTime.now());
        channel.setUpdatedAt(LocalDateTime.now());
        boolean success = channelService.save(channel);
        return success ? ResponseEntity.ok(channel) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Channel> updateChannel(@PathVariable Long id, @RequestBody Channel channel) {
        channel.setId(id);
        channel.setUpdatedAt(LocalDateTime.now());
        boolean success = channelService.updateById(channel);
        return success ? ResponseEntity.ok(channel) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id) {
        boolean success = channelService.removeById(id);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/page")
    public ResponseEntity<Page<Channel>> getChannelsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Channel> page = channelService.page(new Page<>(current, size));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<Channel>> getEnabledChannels() {
        QueryWrapper<Channel> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        List<Channel> channels = channelService.list(wrapper);
        return ResponseEntity.ok(channels);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Channel>> getChannelsByType(@PathVariable Integer type) {
        QueryWrapper<Channel> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        List<Channel> channels = channelService.list(wrapper);
        return ResponseEntity.ok(channels);
    }
}
