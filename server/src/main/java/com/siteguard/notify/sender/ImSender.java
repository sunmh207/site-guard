package com.siteguard.notify.sender;

import com.siteguard.notify.dto.TestWebhookResult;
import com.siteguard.notify.entity.ImRobot;
import com.siteguard.notify.enums.RobotPlatform;

/// IM 机器人发送器接口
///
/// 每种 IM 平台一个实现，由 NotificationConfig 注入到 EnumMap 中分发。
///
/// title 与 message 拆分：
/// - title 用于卡片标题（钉钉 markdown.title、飞书 interactive.card.header.title）
/// - message 为 markdown 内容主体（含 emoji 前缀与站点链接）
public interface ImSender {

    /// 平台枚举（用于 NotificationConfig 构建分发 Map 时作为 key）
    RobotPlatform platform();

    /// 给指定机器人发送一条 markdown 消息；失败抛 RuntimeException，调用方需自行处理
    void send(ImRobot robot, String title, String message);

    /// 测试 Webhook 联通性：发送最小文本消息并按平台返回体解析结果
    TestWebhookResult testWebhook(String webhookUrl, String secret);
}