package com.siteguard.monitor.entity;

/// 站点一次探测的最终结果状态。
///
/// - UP: HTTP 2xx 响应
/// - DOWN: HTTP 非 2xx 响应（包含 3xx 重定向后跟随到的非 2xx）
/// - TIMEOUT: 请求超过 5 秒未完成
/// - ERROR: 连接失败、SSL 错误等其他异常
///
/// 该枚举独立于 [com.siteguard.site.entity.SiteStatus]：单次探测结果包含 4 态，
/// 而 site 表上的快照只存 3 态（UNKNOWN / UP / DOWN），TIMEOUT 与 ERROR 归一为 DOWN。
public enum CheckStatus {
    UP,
    DOWN,
    TIMEOUT,
    ERROR
}