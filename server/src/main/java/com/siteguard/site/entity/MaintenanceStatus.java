package com.siteguard.site.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/// 站点运维时段运行态,供 SiteDTO 只读 view 展示、前端徽标使用。
///
/// - NONE:      站点未启用运维时段(默认)
/// - ACTIVE:    站点已配置运维时段,且此刻正落在窗口内(运维进行中)
/// - SCHEDULED: 站点已配置运维时段,但此刻不在窗口内(计划态,下一窗口待生效)
public enum MaintenanceStatus {

    NONE,
    ACTIVE,
    SCHEDULED;

    /// 根据站点此刻状态计算枚举值。无任何配置 → [NONE]。
    public static MaintenanceStatus of(Site site, Instant nowInstant) {
        MaintenanceWindow w = site.maintenanceWindow();
        if (w == null || w.isEmpty()) {
            return NONE;
        }
        return SiteMaintenance.isInMaintenance(site, nowInstant, w) ? ACTIVE : SCHEDULED;
    }

    /// 向后兼容:LocalDateTime 解读为 [SiteMaintenance#DEFAULT_ZONE] wall-clock 时间。
    public static MaintenanceStatus of(Site site, LocalDateTime now) {
        return of(site, now.atZone(SiteMaintenance.DEFAULT_ZONE).toInstant());
    }
}
