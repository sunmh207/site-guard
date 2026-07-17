package com.siteguard.system.enums;

import com.siteguard.common.exception.Errors;
import lombok.Getter;

/// 系统配置键注册中心。
///
/// 每个 key 关联一个值类型；写入时根据类型做反序列化校验。
/// 新增配置项时在此枚举加一行即可。
public enum ConfigKey {

    /// 通知渠道配置（单机器人）
    NOTIFICATION("notification", com.siteguard.system.config.NotificationConfig.class),

    /// 证书告警阈值配置（多档预警天数）
    CERT_ALERT("cert_alert", com.siteguard.system.config.CertAlertConfig.class),

    /// 连续失败阈值配置：连续 N 次失败才触发告警，默认 1（即单次失败立即告警）。
    /// null / 未配置时走 ConsecutiveFailureConfig.defaultValue()
    CONSECUTIVE_FAILURES_BEFORE_ALERT(
            "consecutive_failures_before_alert",
            com.siteguard.system.config.ConsecutiveFailureConfig.class),

    /// 公开大屏开关：默认 false（关闭）；为 true 时 /api/v1/open/site/stats/dashboard 才返回数据。
    /// OpenDashboardController 自身读取这个 key 做访问闸门，避免「无开关、内网即可访问」的隐式行为。
    /// 用 Boolean 而不是 String 是个有意区分：拒绝"open_dashboard=1/0/yes/no"这类模糊语义。
    OPEN_DASHBOARD("open_dashboard", Boolean.class),

    /// 定时发送报告配置：每日定时把 dashboard 快照（摘要 + 异常列表）推送到已配置的通知机器人。
    /// 默认不启用（enabled=false）；time 默认 "08:00"。启用前提：NOTIFICATION 已配置且 enabled=true。
    /// 调度器 ScheduledReportScheduler 每分钟检查一次是否到点、是否今日已发。
    SCHEDULED_REPORT("scheduled_report", com.siteguard.system.config.ScheduledReportConfig.class);

    @Getter
    private final String key;
    @Getter
    private final Class<?> valueType;

    ConfigKey(String key, Class<?> valueType) {
        this.key = key;
        this.valueType = valueType;
    }

    /// 从字符串解析（URL 路径 / 请求体 key 字段）
    public static ConfigKey fromString(String key) {
        for (ConfigKey k : values()) {
            if (k.key.equals(key)) return k;
        }
        throw Errors.INVALID_ARGUMENT.toException("未知配置键: {}", key);
    }
}
