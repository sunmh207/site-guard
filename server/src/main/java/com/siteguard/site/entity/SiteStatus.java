package com.siteguard.site.entity;

/// 站点可用性状态枚举
/// - UNKNOWN: 从未检测（默认值）
/// - UP: 在线
/// - DOWN: 离线
public enum SiteStatus {
    UNKNOWN,
    UP,
    DOWN
}