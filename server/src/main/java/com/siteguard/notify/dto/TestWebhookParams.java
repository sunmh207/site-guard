package com.siteguard.notify.dto;

import com.siteguard.notify.enums.RobotPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/// 测试 Webhook 联通性参数
@Data
@Schema(description = "测试 Webhook 联通性参数")
public class TestWebhookParams {

    @NotNull
    @Schema(description = "平台", requiredMode = Schema.RequiredMode.REQUIRED)
    private RobotPlatform platform;

    @NotBlank
    @Length(max = 1024)
    @Pattern(regexp = "^https?://.+", message = "Webhook URL 必须以 http:// 或 https:// 开头")
    @Schema(description = "Webhook URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String webhookUrl;

    @Schema(description = "签名密钥（可空）")
    private String secret;
}
