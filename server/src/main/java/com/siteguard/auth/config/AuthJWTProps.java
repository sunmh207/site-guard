package com.siteguard.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/// JWT认证配置属性类
/// 从配置文件中读取JWT相关的配置信息
@Component
@ConfigurationProperties(prefix = "app.auth.jwt")
@Data
public class AuthJWTProps {
    /// JWT签发者
    /// 标识JWT令牌的签发方
    private String issuer;
    
    /// JWT密钥
    /// 用于签名和验证JWT令牌的密钥
    private String secret;
    
    /// 访问令牌有效期
    /// 访问令牌的过期时间，单位：毫秒
    private Long accessTokenTtl;
    
    /// 刷新令牌有效期
    /// 刷新令牌的过期时间，单位：毫秒
    private Long refreshTokenTtl;
}
