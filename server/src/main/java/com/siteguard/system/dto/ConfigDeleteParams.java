package com.siteguard.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/// 系统配置删除请求。
@Data
@Schema(description = "系统配置删除请求")
public class ConfigDeleteParams {

    @NotBlank
    @Schema(description = "配置键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;
}