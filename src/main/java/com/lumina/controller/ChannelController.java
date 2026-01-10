package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.Channel;
import com.lumina.service.ChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/channels")
public class ChannelController {

    @Autowired
    private ChannelService channelService;

    @GetMapping
    public ApiResponse<List<Channel>> getAllChannels() {
        return ApiResponse.success(channelService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Channel> getChannelById(@PathVariable Long id) {
        Channel channel = channelService.getById(id);
        if (channel == null) {
            throw new IllegalArgumentException("渠道不存在");
        }
        return ApiResponse.success(channel);
    }

    @PostMapping
    public ApiResponse<Channel> createChannel(@RequestBody Channel channel) {
        channel.setCreatedAt(LocalDateTime.now());
        channel.setUpdatedAt(LocalDateTime.now());
        channelService.save(channel);
        return ApiResponse.success("创建成功", channel);
    }

    @PutMapping("/{id}")
    public ApiResponse<Channel> updateChannel(@PathVariable Long id, @RequestBody Channel channel) {
        channel.setId(id);
        channel.setUpdatedAt(LocalDateTime.now());
        channelService.updateById(channel);
        return ApiResponse.success("更新成功", channel);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteChannel(@PathVariable Long id) {
        channelService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<Channel>> getChannelsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(channelService.page(new Page<>(current, size)));
    }

    @GetMapping("/enabled")
    public ApiResponse<List<Channel>> getEnabledChannels() {
        QueryWrapper<Channel> wrapper = new QueryWrapper<>();
        wrapper.eq("is_enabled", true);
        return ApiResponse.success(channelService.list(wrapper));
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<Channel>> getChannelsByType(@PathVariable Integer type) {
        QueryWrapper<Channel> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        return ApiResponse.success(channelService.list(wrapper));
    }
}
