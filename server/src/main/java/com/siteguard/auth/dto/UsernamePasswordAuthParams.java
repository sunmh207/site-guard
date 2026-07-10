package com.siteguard.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
@Schema(description = "用户名密码认证参数，用于用户通过用户名和密码进行身份认证")
public class UsernamePasswordAuthParams {
    @NotBlank
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    private String username;

    @NotBlank
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "password")
    private String password;
}

