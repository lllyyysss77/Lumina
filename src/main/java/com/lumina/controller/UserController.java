package com.lumina.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.User;
import com.lumina.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ApiResponse<List<User>> getAllUsers() {
        return ApiResponse.success(userService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return ApiResponse.success(user);
    }

    @PostMapping
    public ApiResponse<User> createUser(@RequestBody User user) {
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userService.save(user);
        return ApiResponse.success("创建成功", user);
    }

    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        return ApiResponse.success("更新成功", user);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<User>> getUsersByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(userService.page(new Page<>(current, size)));
    }

    @GetMapping("/username/{username}")
    public ApiResponse<User> getUserByUsername(@PathVariable String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        User user = userService.getOne(wrapper);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return ApiResponse.success(user);
    }
}
