package com.siteguard.monitor.alert.notification;

import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;

/// 边沿触发后的通知事件：每个 (siteId, kind, bucket) 变化各发一次。
///
/// 不继承 ApplicationEvent：Spring 4.2+ 支持任意 POJO 作为事件载荷。
/// @Async @EventListener 监听器消费此事件，异步落库 + 投递 IM。
///
/// 携带站点上下文 (siteName / siteUrl) 让监听器在拼装 IM 文本时无需再次查库。
public record NotificationEvent(
        Long siteId,
        String siteName,
        String siteUrl,
        AlertKind alertKind,
        AlertStatus status,
        String bucket,
        String message,
        long detectedAt
) {}