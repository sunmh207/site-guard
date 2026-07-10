package com.siteguard.monitor.alert.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

/// site_check_state 表的复合主键：(siteId, alertKind, bucket)。
///
/// - alertKind 以 String 存储（对应 AlertKind 枚举名），与表 VARCHAR(32) 对齐
/// - bucket 在 PATH_CHECK 时即为 pathKey（如 "/api/orders"）；其他 kind 为状态档位
///   （如 "UP"/"DOWN"/"W7"/"OK"）
@Embeddable
public record SiteCheckStateId(
        @Column(name = "site_id", nullable = false) Long siteId,
        @Column(name = "alert_kind", nullable = false, length = 32) String alertKind,
        @Column(name = "bucket", nullable = false, length = 32) String bucket
) implements Serializable {
}
