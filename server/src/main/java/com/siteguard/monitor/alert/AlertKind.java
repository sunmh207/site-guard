package com.siteguard.monitor.alert;

/// 告警种类。聚合多源告警到统一的 AlertDTO 中。
///
/// 当前承载：
/// - AVAILABILITY    : 单次可用性探测非 UP 的事件
/// - CERT_EXPIRY     : 证书即将过期或已过期
/// - DOMAIN_EXPIRING : 域名即将过期或已过期
/// - PATH_CHECK      : 站点自定义子路由（如 /app_dev.php）探测返回非期望状态码的事件
///
/// 扩展性：未来 PATH_PROBE_FAILED / PATH_NOT_FOUND_EXPECTED 等仅在本 enum 上新增值，
/// 不修改 AlertDTO / DashboardAlertAggregationService。
public enum AlertKind {
    AVAILABILITY,
    CERT_EXPIRY,
    DOMAIN_EXPIRING,
    PATH_CHECK
}
