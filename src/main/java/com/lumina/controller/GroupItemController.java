package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.GroupItem;
import com.lumina.service.GroupItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/group-items")
public class GroupItemController {

    @Autowired
    private GroupItemService groupItemService;

    @GetMapping
    public ApiResponse<List<GroupItem>> getAllGroupItems() {
        List<GroupItem> groupItems = groupItemService.list();
        return ApiResponse.success(groupItems);
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupItem> getGroupItemById(@PathVariable Long id) {
        GroupItem groupItem = groupItemService.getById(id);
        if (groupItem == null) {
            throw new IllegalArgumentException("GroupItem not found with id: " + id);
        }
        return ApiResponse.success(groupItem);
    }

    @PostMapping
    public ApiResponse<GroupItem> createGroupItem(@RequestBody GroupItem groupItem) {
        groupItem.setCreatedAt(LocalDateTime.now());
        groupItem.setUpdatedAt(LocalDateTime.now());
        boolean success = groupItemService.save(groupItem);
        if (!success) {
            throw new IllegalArgumentException("Failed to create group item");
        }
        return ApiResponse.success(groupItem);
    }

    @PutMapping("/{id}")
    public ApiResponse<GroupItem> updateGroupItem(@PathVariable Long id, @RequestBody GroupItem groupItem) {
        groupItem.setId(id);
        groupItem.setUpdatedAt(LocalDateTime.now());
        boolean success = groupItemService.updateById(groupItem);
        if (!success) {
            throw new IllegalArgumentException("GroupItem not found with id: " + id);
        }
        return ApiResponse.success(groupItem);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGroupItem(@PathVariable Long id) {
        boolean success = groupItemService.removeById(id);
        if (!success) {
            throw new IllegalArgumentException("GroupItem not found with id: " + id);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<GroupItem>> getGroupItemsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<GroupItem> page = groupItemService.page(new Page<>(current, size));
        return ApiResponse.success(page);
    }

    @GetMapping("/group/{groupId}")
    public ApiResponse<List<GroupItem>> getGroupItemsByGroupId(@PathVariable Long groupId) {
        QueryWrapper<GroupItem> wrapper = new QueryWrapper<>();
        wrapper.eq("group_id", groupId);
        List<GroupItem> groupItems = groupItemService.list(wrapper);
        return ApiResponse.success(groupItems);
    }

    @GetMapping("/channel/{channelId}")
    public ApiResponse<List<GroupItem>> getGroupItemsByChannelId(@PathVariable Long channelId) {
        QueryWrapper<GroupItem> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId);
        List<GroupItem> groupItems = groupItemService.list(wrapper);
        return ApiResponse.success(groupItems);
    }
}
