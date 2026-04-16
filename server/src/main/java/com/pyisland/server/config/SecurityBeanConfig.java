package com.pyisland.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 定义。
 */
@Configuration
public class SecurityBeanConfig {

    /**
     * 密码哈希器：使用 BCrypt 强哈希。
     * @return PasswordEncoder 实例。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
