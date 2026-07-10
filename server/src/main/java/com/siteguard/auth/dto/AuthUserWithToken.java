package com.siteguard.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "完整认证用户信息，继承自 AuthUser，额外包含刷新令牌信息，通常在用户登录时返回")
public class AuthUserWithToken {

    @Schema(description = "当前登录用户")
    private AuthUser user;

    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "访问令牌生命周期（毫秒）", example = "3600000")
    private Long accessTokenTtl;

    @Schema(description = "刷新令牌，用于在访问令牌过期后获取新的访问令牌，避免用户重新登录", example = "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...")
    private String refreshToken;

    @Schema(description = "刷新令牌生命周期（毫秒）", example = "604800000")
    private Long refreshTokenTtl;
}
