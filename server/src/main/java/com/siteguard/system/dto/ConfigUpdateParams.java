package com.siteguard.system.dto;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/// 系统配置保存请求。
@Data
@Schema(description = "系统配置保存请求")
public class ConfigUpdateParams {

    @NotBlank
    @Schema(description = "配置键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @NotNull
    @Schema(description = "配置值（任意 JSON）", requiredMode = Schema.RequiredMode.REQUIRED)
    private JsonNode value;
}