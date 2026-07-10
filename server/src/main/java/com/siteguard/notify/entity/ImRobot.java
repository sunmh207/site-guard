package com.siteguard.notify.entity;

import com.siteguard.notify.enums.RobotPlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// IM 机器人内部数据载体。
///
/// 不再是 JPA 实体，仅作为 NotifyService → ImSender 之间的传输对象。
/// 持久化由 system_config.config_value 中的 JSON 承担。
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImRobot {
    private RobotPlatform platform;
    private String webhookUrl;
    private String secret;
    private Boolean enabled;
}