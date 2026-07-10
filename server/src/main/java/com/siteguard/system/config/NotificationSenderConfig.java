package com.siteguard.system.config;

import com.siteguard.notify.enums.RobotPlatform;
import com.siteguard.notify.sender.ImSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.List;

/// 把 Spring 容器中所有 ImSender 按 platform() 聚合到 EnumMap，
/// 供 NotifyServiceImpl 按平台查找。
///
/// 原文件名：notify/config/NotificationConfig.java（与本包 NotificationConfig POJO 重名）。
/// 搬迁到 system 包后改名 NotificationSenderConfig 以避免命名冲突。
@Configuration
public class NotificationSenderConfig {

    @Bean
    public EnumMap<RobotPlatform, ImSender> senderMap(List<ImSender> senders) {
        EnumMap<RobotPlatform, ImSender> map = new EnumMap<>(RobotPlatform.class);
        for (ImSender s : senders) {
            map.put(s.platform(), s);
        }
        return map;
    }
}