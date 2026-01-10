package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.GroupItem;
import com.lumina.service.GroupItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/group-items")
public class GroupItemController {

    @Autowired
    private GroupItemService groupItemService;

    @GetMapping
    public ResponseEntity<List<GroupItem>> getAllGroupItems() {
        List<GroupItem> groupItems = groupItemService.list();
        return ResponseEntity.ok(groupItems);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupItem> getGroupItemById(@PathVariable Long id) {
        GroupItem groupItem = groupItemService.getById(id);
        if (groupItem == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(groupItem);
    }

    @PostMapping
    public ResponseEntity<GroupItem> createGroupItem(@RequestBody GroupItem groupItem) {
        groupItem.setCreatedAt(LocalDateTime.now());
        groupItem.setUpdatedAt(LocalDateTime.now());
        boolean success = groupItemService.save(groupItem);
        return success ? ResponseEntity.ok(groupItem) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupItem> updateGroupItem(@PathVariable Long id, @RequestBody GroupItem groupItem) {
        groupItem.setId(id);
        groupItem.setUpdatedAt(LocalDateTime.now());
        boolean success = groupItemService.updateById(groupItem);
        return success ? ResponseEntity.ok(groupItem) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroupItem(@PathVariable Long id) {
        boolean success = groupItemService.removeById(id);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/page")
    public ResponseEntity<Page<GroupItem>> getGroupItemsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<GroupItem> page = groupItemService.page(new Page<>(current, size));
        return ResponseEntity.ok(page);
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<GroupItem>> getGroupItemsByGroupId(@PathVariable Long groupId) {
        QueryWrapper<GroupItem> wrapper = new QueryWrapper<>();
        wrapper.eq("group_id", groupId);
        List<GroupItem> groupItems = groupItemService.list(wrapper);
        return ResponseEntity.ok(groupItems);
    }

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<GroupItem>> getGroupItemsByChannelId(@PathVariable Long channelId) {
        QueryWrapper<GroupItem> wrapper = new QueryWrapper<>();
        wrapper.eq("channel_id", channelId);
        List<GroupItem> groupItems = groupItemService.list(wrapper);
        return ResponseEntity.ok(groupItems);
    }
}
