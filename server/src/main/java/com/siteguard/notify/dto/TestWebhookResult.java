package com.siteguard.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 测试 Webhook 结果
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "测试 Webhook 结果")
public class TestWebhookResult {
    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "成功时为 ok 或 errmsg；失败时为失败原因")
    private String message;

    public static TestWebhookResult ok(String message) {
        return new TestWebhookResult(true, message);
    }

    public static TestWebhookResult fail(String message) {
        return new TestWebhookResult(false, message);
    }
}
