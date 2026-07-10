package com.siteguard.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson配置类
 */
@Configuration
public class JacksonConfig {

    /**
     * 配置ObjectMapper Bean
     */
    @Bean
    public ObjectMapper objectMapper() {
        // Jackson 3.x：ObjectMapper 不可变，经 builder 构造；此处保持默认配置
        return JsonMapper.builder().build();
    }
}