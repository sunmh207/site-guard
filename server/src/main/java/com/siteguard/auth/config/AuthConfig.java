package com.siteguard.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/// 认证配置类
/// 配置用户认证相关的Bean，包括密码编码器等
@Configuration
public class AuthConfig {
    /// 密码编码器Bean
    /// 使用BCrypt算法对密码进行加密和验证
    ///
    /// @return BCrypt密码编码器实例
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
