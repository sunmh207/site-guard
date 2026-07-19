package com.siteguard.site.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// SiteMaintenance 纯函数测试。覆盖:
/// - 未启用(空 / NONE)→ false
/// - 普通窗口 / 跨日窗口
/// - 星期过滤
/// - 边界(t == start 命中 / t == end 不命中)
///
/// 所有用例直接传 MaintenanceWindow,绕过 Site.getMaintenance 解析;解析本身由 MaintenanceWindowTest 覆盖。
class SiteMaintenanceTest {

    private static final Site SITE = new Site();

    private static MaintenanceWindow w(String start, String end, List<String> days) {
        // 绕开 parse:直接用内部结构。这里仍走 parse 以贴近真实路径。
        StringBuilder sb = new StringBuilder();
        sb.append("{\"start\":\"").append(start).append("\",\"end\":\"").append(end).append("\"");
        if (days != null && !days.isEmpty()) {
            sb.append(",\"days\":[");
            for (int i = 0; i < days.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(days.get(i)).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        return MaintenanceWindow.parse(sb.toString());
    }

    @Test
    void emptyWindow_neverInMaintenance() {
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 23, 0), MaintenanceWindow.NONE));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 23, 0),
                MaintenanceWindow.parse((String) null)));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 23, 0),
                MaintenanceWindow.parse("")));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 23, 0),
                MaintenanceWindow.parse("{}")));
    }

    @Test
    void sameDayWindow_hitsAndMisses() {
        var w = w("09:00", "18:00", null);  // 全周
        // 窗口内
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 9, 0), w));
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 12, 30), w));
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 17, 59), w));
        // 窗口外
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 8, 59), w));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 18, 0), w));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 23, 0), w));
    }

    @Test
    void crossMidnight_window_hitsAndMisses() {
        // 最常见的运维窗口:22:00-次日 08:00
        var w = w("22:00", "08:00", null);
        // 窗口内:22:00 起 / 00:30 / 07:59
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 22, 0), w));
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 23, 59), w));
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 0, 0), w));
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 7, 59), w));
        // 窗口外:08:00 整不再命中 / 12:00
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 8, 0), w));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 12, 0), w));
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 21, 59), w));
    }

    @Test
    void crossMidnight_window_respectsDaysOfWeek() {
        // 工作日(周一~周五)夜间运维
        var w = w("22:00", "08:00", List.of("MON", "TUE", "WED", "THU", "FRI"));

        // 2026-07-20 是周一(查看 DayOfWeek):窗口内周一 23:00 → 命中
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 20, 23, 0), w));
        // 2026-07-20 周一 06:00(还在次日窗口)→ 命中
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 20, 6, 0), w));

        // 2026-07-19 是周日:同时间 06:00 → 不在工作日,不命中
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 6, 0), w));
        // 2026-07-19 周日 23:00 → 不在工作日,不命中
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 19, 23, 0), w));
    }

    @Test
    void boundary_startInclusive_endExclusive() {
        var w = w("09:00", "18:00", null);
        // t == start → 命中
        assertTrue(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 9, 0), w));
        // t == end → 不命中(左闭右开)
        assertFalse(SiteMaintenance.isInMaintenance(SITE, LocalDateTime.of(2026, 7, 18, 18, 0), w));
    }

    @Test
    void defaultSiteMaintenance_withSite() {
        // 使用 Site.maintenanceWindow() 解析路径:未启用站点任何时刻都 false
        var site = new Site();
        site.setMaintenance(null);
        assertFalse(SiteMaintenance.isInMaintenance(site, LocalDateTime.of(2026, 7, 18, 23, 0)));

        // 启用跨日窗口
        site.setMaintenance("{\"start\":\"22:00\",\"end\":\"08:00\"}");
        assertTrue(SiteMaintenance.isInMaintenance(site, LocalDateTime.of(2026, 7, 18, 23, 0)));
        assertFalse(SiteMaintenance.isInMaintenance(site, LocalDateTime.of(2026, 7, 18, 12, 0)));
    }
}
