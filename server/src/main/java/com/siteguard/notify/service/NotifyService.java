package com.siteguard.notify.service;

import com.siteguard.notify.dto.TestWebhookParams;
import com.siteguard.notify.dto.TestWebhookResult;
import com.siteguard.notify.enums.RobotPlatform;

/// 通知发送服务。
///
/// send：从 ConfigService 读取通知配置；若未设置或 enabled=false 抛 INVALID_ARGUMENT。
/// testWebhook：不依赖配置存储，直接验证 Webhook 联通性。
///
/// title 与 message 拆分：title 用作卡片标题，message 为 markdown 正文。
public interface NotifyService {

    void send(String title, String message);

    TestWebhookResult testWebhook(RobotPlatform platform, String webhookUrl, String secret);

    /// 适配层：把 TestWebhookParams 转为三个独立参数。
    default TestWebhookResult testWebhook(TestWebhookParams params) {
        return testWebhook(params.getPlatform(), params.getWebhookUrl(), params.getSecret());
    }
}