package com.siteguard.system.dto;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 系统配置读取响应。
///
/// value 为原始 JsonNode，前端按需解析为具体类型（如 NotificationConfig）。
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统配置响应")
public class ConfigResponse {

    @Schema(description = "配置键")
    private String key;

    @Schema(description = "配置值（原始 JSON）")
    private JsonNode value;

    @Schema(description = "更新时间（毫秒）")
    private Long updatedAt;
}