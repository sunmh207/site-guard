package com.siteguard.notify.service.impl;

import com.siteguard.common.exception.Errors;
import com.siteguard.notify.dto.TestWebhookResult;
import com.siteguard.notify.entity.ImRobot;
import com.siteguard.notify.enums.RobotPlatform;
import com.siteguard.notify.sender.ImSender;
import com.siteguard.notify.service.NotifyService;
import com.siteguard.system.config.NotificationConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;

/// 通知发送实现：
///   - send：从 ConfigService 读取 NotificationConfig，按 platform 查找 ImSender，转发消息
///   - testWebhook：直接按 platform 查找 ImSender，调用 testWebhook 不依赖数据库
@Service
@RequiredArgsConstructor
@Slf4j
public class NotifyServiceImpl implements NotifyService {

    private final ConfigService configService;
    private final EnumMap<RobotPlatform, ImSender> senderMap;

    @Override
    public void send(String title, String message) {
        /// 显式类型参数让 var 推导为 NotificationConfig，避免 Object 调用 getter 失败
        var cfg = configService.<NotificationConfig>get(ConfigKey.NOTIFICATION);
        if (cfg == null) {
            throw Errors.INVALID_ARGUMENT.toException("尚未配置通知渠道");
        }
        if (cfg.getEnabled() == null || !cfg.getEnabled()) {
            throw Errors.INVALID_ARGUMENT.toException("通知渠道未启用");
        }
        var sender = senderMap.get(cfg.getPlatform());
        if (sender == null) {
            throw Errors.INVALID_ARGUMENT.toException("不支持的平台: {}", cfg.getPlatform());
        }
        var robot = ImRobot.builder()
            .platform(cfg.getPlatform())
            .webhookUrl(cfg.getWebhookUrl())
            .secret(cfg.getSecret())
            .enabled(cfg.getEnabled())
            .build();
        log.info("发送通知，平台: {} 标题: {}", robot.getPlatform(), title);
        sender.send(robot, title, message);
    }

    @Override
    public TestWebhookResult testWebhook(RobotPlatform platform, String webhookUrl, String secret) {
        var sender = senderMap.get(platform);
        if (sender == null) {
            throw Errors.INVALID_ARGUMENT.toException("不支持的平台: {}", platform);
        }
        return sender.testWebhook(webhookUrl, secret);
    }
}