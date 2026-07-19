package com.siteguard.site.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/// 站点运维时段判定工具。
///
/// 单一入口 [isInMaintenance]:在指定时刻,站点是否落在任一运维窗口内。
/// 判定借道站点 paused 同一路径 —— 运维时段视为"按时间表自动暂停"。
///
/// 时区:默认按 [#DEFAULT_ZONE](Asia/Shanghai) 解读运维窗口的"几点几分"。
/// 因为运维时间配置是本地直觉(站长写 22:00 指的是北京时间 22:00),中国不用 DST。
/// 真要海外部署时再加 `maintenance_tz` 列,本工具预留 `ZoneId` 参数重载。
///
/// 性能:判定是纯算术 O(1);在 checkAll / detectAll 阶段每个站点算一次,无 N+1。
public final class SiteMaintenance {

    /// 默认时区:运维窗口的"几点几分"按此解读。
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private SiteMaintenance() {
    }

    /// true = 站点此刻落在运维时段内,调度层应跳过探测、告警层应跳过检测。
    public static boolean isInMaintenance(Site site, Instant nowInstant) {
        return isInMaintenance(site, nowInstant, site.maintenanceWindow());
    }

    /// 便于测试直传 window,绕过 Site 解析。
    public static boolean isInMaintenance(Site site, Instant nowInstant, MaintenanceWindow w) {
        if (w == null || w.isEmpty()) {
            return false;
        }
        return isInMaintenance(nowInstant, w, DEFAULT_ZONE);
    }

    /// 完整重载:显式指定时区(未来多时区部署时使用;默认时区可调用其他重载)。
    public static boolean isInMaintenance(Instant nowInstant, MaintenanceWindow w, ZoneId zone) {
        if (w == null || w.isEmpty()) {
            return false;
        }
        ZonedDateTime now = nowInstant.atZone(zone);
        // 1) 星期过滤(空列表 = 全周,直接放行)
        if (!w.days().isEmpty() && !w.days().contains(dayShort(now.getDayOfWeek()))) {
            return false;
        }
        // 2) 时间判定:start ＜ end 为普通窗口;start ＞ end 为跨日窗口(22:00-08:00)
        LocalTime t = now.toLocalTime();
        LocalTime s = w.startTime();
        LocalTime e = w.endTime();
        if (s.isBefore(e)) {
            return !t.isBefore(s) && t.isBefore(e);
        }
        return !t.isBefore(s) || t.isBefore(e);
    }

    /// 向后兼容:接受 LocalDateTime 的测试直接解读为 [DEFAULT_ZONE] 的 wall-clock 时间。
    public static boolean isInMaintenance(Site site, LocalDateTime now) {
        return isInMaintenance(site, now.atZone(DEFAULT_ZONE).toInstant());
    }

    /// 向后兼容:接受 LocalDateTime 的测试直接解读为 [DEFAULT_ZONE] 的 wall-clock 时间。
    public static boolean isInMaintenance(Site site, LocalDateTime now, MaintenanceWindow w) {
        return isInMaintenance(site, now.atZone(DEFAULT_ZONE).toInstant(), w);
    }

    /// DayOfWeek → 3 字母短名(MON..SUN),与 MaintenanceWindow.days 存储约定对齐。
    private static String dayShort(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }
}
