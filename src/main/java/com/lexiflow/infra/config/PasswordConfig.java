package com.lexiflow.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置类。
 * <p>
 * 配置 Spring Security 使用的密码编码器，采用 BCrypt 算法。
 * </p>
 */
@Configuration
public class PasswordConfig {

    /** 创建 BCrypt 密码编码器实例。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
