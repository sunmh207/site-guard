package com.siteguard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性类
 */
@Component
@ConfigurationProperties(prefix = "auth.jwt")
@Getter
@Setter
public class JwtProperties {
    private String issuer = "ai-courseware";
    private String secret = "ChangeThisSecretKeyInProduction";
    private long accessTokenTtl = 1800000L;      // 30 分钟
    private long refreshTokenTtl = 2592000000L;  // 30 天
}

