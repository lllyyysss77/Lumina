package com.lumina.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.Group;
import com.lumina.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @GetMapping
    public ApiResponse<List<Group>> getAllGroups() {
        List<Group> groups = groupService.list();
        return ApiResponse.success(groups);
    }

    @GetMapping("/{id}")
    public ApiResponse<Group> getGroupById(@PathVariable Long id) {
        Group group = groupService.getGroupById(id);
        if (group == null) {
            throw new IllegalArgumentException("Group not found with id: " + id);
        }
        return ApiResponse.success(group);
    }

    @PostMapping
    public ApiResponse<Group> createGroup(@RequestBody Group group) {
        groupService.createGroup(group);
        return ApiResponse.success(group);
    }

    @PutMapping("/{id}")
    public ApiResponse<Group> updateGroup(@PathVariable Long id, @RequestBody Group group) {
        groupService.updateGroup(id,group);
        return ApiResponse.success(group);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<Group>> getGroupsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {
        Page<Group> page = groupService.getGroupsByPage(new Page<>(current, size), name);
        return ApiResponse.success(page);
    }
}
