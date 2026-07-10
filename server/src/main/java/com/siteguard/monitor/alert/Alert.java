package com.siteguard.monitor.alert;

import java.util.List;

/// 检测器对单个站点一次探测的输出结果。
///
/// 检测器本身是无状态的：纯函数 (Site, Clock) → Set<EvalResult>（详见 AlertDefinition）。
/// 边沿判断、事件发布、状态存储全部由 AlertDetectionService 负责。
///
/// 当前实现保留 Alert 数据类用于：
/// - NotificationEvent / Notification 行内容
/// - NotificationListener 中 message 透传
///
/// 字段语义：
/// - kind       : 告警维度（可用性 / 证书 / 域名 / 子路由）
/// - status     : NORMAL = 当前 bucket 无需告警；ABNORMAL = 需要告警
/// - bucket     : 在 (kind, status) 维度下的具体档位（如 "UP"/"DOWN"/"W7"/"EXPIRED"，
///                PATH_CHECK 时即为 pathKey 如 "/api/orders"）
/// - message    : 给运维/IM 看的人类可读消息
/// - detectedAt : 探测时间戳（毫秒），由 Clock 注入保证可测
public record Alert(
        AlertKind kind,
        AlertStatus status,
        String bucket,
        String message,
        long detectedAt
) {

    public static Alert normal(AlertKind kind, String bucket, long detectedAt) {
        return new Alert(kind, AlertStatus.NORMAL, bucket, recoveryMessage(kind), detectedAt);
    }

    public static Alert abnormal(AlertKind kind, String bucket, String message, long detectedAt) {
        return new Alert(kind, AlertStatus.ABNORMAL, bucket, message, detectedAt);
    }

    /// NORMAL（恢复）消息按 kind 区分：运维看一眼就能知道是可用性、证书还是域名恢复。
    /// PATH_CHECK 的恢复消息由 AlertDetectionService 基于 bucket (= pathKey) 拼装：
    /// "子路由 `/api/orders` 已恢复"。
    ///
    /// 公开：AlertDetectionService 在"级变恢复"时复用此模板避免消息漂移。
    /// 模板与 normal() 工厂共用——保持 NORMAL 事件消息的唯一来源。
    public static String recoveryMessage(AlertKind kind) {
        return switch (kind) {
            case AVAILABILITY -> "可用性已恢复";
            case CERT_EXPIRY -> "证书有效期已恢复";
            case DOMAIN_EXPIRING -> "域名有效期已恢复";
            case PATH_CHECK -> "子路由检测已恢复";
        };
    }
}
