package com.siteguard.system.config;

import com.siteguard.notify.enums.RobotPlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 通知渠道配置（存储于 system_config.config_value，JSON 序列化）。
///
/// 字段语义：
///   - enabled: 是否启用；null/false → NotifyService 拒绝发送
///   - platform: IM 平台枚举
///   - webhookUrl: 平台 Webhook URL（必须 http(s)://）
///   - secret: 签名密钥（钉钉/飞书加签用，企微可选）；编辑留空表示保持不变
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationConfig {

    private Boolean enabled;
    private RobotPlatform platform;
    private String webhookUrl;
    private String secret;
}