package com.lumina;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
public class PasswordTest {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    public void encodedPassword() {
        System.out.println(passwordEncoder.encode("admin123"));
        System.out.println(passwordEncoder.matches("admin123", "$2a$10$vCpowgtGLo295h1rkSvuHeLHC4GXtn/9oT47e0wtaDQpr0ScEsaQG"));
    }
}