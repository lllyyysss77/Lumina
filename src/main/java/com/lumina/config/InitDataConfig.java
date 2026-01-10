package com.lumina.config;

import com.lumina.entity.User;
import com.lumina.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitDataConfig implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已经存在 admin 用户
        User existingAdmin = userService.lambdaQuery()
                .eq(User::getUsername, "admin")
                .one();

        if (existingAdmin == null) {
            // 创建管理员用户
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            userService.save(admin);
            System.out.println("Created default admin user: admin/admin123");
        }
    }
}