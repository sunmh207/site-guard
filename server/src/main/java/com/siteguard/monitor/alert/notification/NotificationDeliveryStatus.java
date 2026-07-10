package com.siteguard.monitor.alert.notification;

/// 通知投递状态。
///
/// - PENDING  : 已写入通知表，待发送
/// - SUCCESS  : 已成功投递到 IM 渠道
/// - FAILED   : 发送失败（IM 未配置 / 渠道禁用 / 网络异常），error_message 记录原因
public enum NotificationDeliveryStatus {
    PENDING,
    SUCCESS,
    FAILED
}
