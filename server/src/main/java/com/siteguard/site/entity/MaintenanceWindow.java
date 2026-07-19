package com.siteguard.site.entity;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 站点运维时段的文本化工具：Site.maintenance 列（VARCHAR, nullable）
/// 存储人类可读的 JSON 对象，本工具负责它与 Java [MaintenanceWindow] 的双向转换。
///
/// 结构：
/// ```json
/// {"start":"22:00","end":"08:00","days":["MON","TUE","WED","THU","FRI"]}
/// ```
/// - start / end: 24 小时制 "HH:mm",不可相等；start ＞ end 视为跨日窗口(22:00-08:00)
/// - days: 可选,不传 = 全周(最常见)；合法值 = java.time.DayOfWeek 的 getDisplayName 短名(MON..SUN)
///
/// 解析兜底:null / 空 / 解析失败 / 语义非法 → [MaintenanceWindow#NONE](空,等价于"未启用")。
/// 内部用独立 static ObjectMapper,与 Spring 容器里的 Bean 解耦(同 CertForgive)。
public final class MaintenanceWindow {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /// 合法 3 字母星期短名集合(MON..SUN)。
    public static final Set<String> ALL_DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    /// 把站点级 maintenance 文本解析为不可变值对象。
    /// null / 空 / 解析失败 / 语义非法(start==end / 非法天数 等) → [NONE]。
    public static MaintenanceWindow parse(String json) {
        if (json == null || json.isBlank()) {
            return NONE;
        }
        Raw raw;
        try {
            raw = MAPPER.readValue(json, Raw.class);
        } catch (Exception e) {
            // 数据损坏:整体退回 NONE,站点按"无运维时段"处理(24h 监控)
            return NONE;
        }
        return fromRaw(raw);
    }

    /// 把值对象序列化为 JSON 对象字符串。[NONE] / null → null(让列保持 NULL,= 禁用语义)。
    public static String json(MaintenanceWindow w) {
        if (w == null || w.equals(NONE)) {
            return null;
        }
        var raw = new Raw();
        raw.start = w.startTime().toString();
        raw.end = w.endTime().toString();
        raw.days = w.days().isEmpty() ? null : w.days();
        try {
            return MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /// 默认实例:未启用(任何判定都返回 false)。
    public static final MaintenanceWindow NONE = new MaintenanceWindow(null, null, List.of());

    private final LocalTime startTime;
    private final LocalTime endTime;
    private final List<String> days;

    private MaintenanceWindow(LocalTime start, LocalTime end, List<String> days) {
        this.startTime = start;
        this.endTime = end;
        this.days = days;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public LocalTime endTime() {
        return endTime;
    }

    /// 空列表 = 全周。
    public List<String> days() {
        return days;
    }

    public boolean isEmpty() {
        return this.equals(NONE);
    }

    /// 值语义相等:start / end / day 列表都相等。
    /// 注意:days 顺序敏感(用 List.equals),保持输入顺序。
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaintenanceWindow that)) return false;
        return Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime)
                && Objects.equals(days, that.days);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime, days);
    }

    /// 反序列化中间结构,字段名与 JSON key 对齐。
    @SuppressWarnings("unused")
    private static class Raw {
        public String start;
        public String end;
        public List<String> days;
    }

    private static MaintenanceWindow fromRaw(Raw raw) {
        if (raw == null) {
            return NONE;
        }
        // start / end 必填,缺失或格式非法 → NONE
        if (raw.start == null || raw.end == null) {
            return NONE;
        }
        LocalTime s;
        LocalTime e;
        try {
            s = LocalTime.parse(raw.start);
            e = LocalTime.parse(raw.end);
        } catch (Exception ex) {
            return NONE;
        }
        // start 不可等于 end(要么 0 长度,要么 24 小时,都是二义性 → 拒绝)
        if (s.equals(e)) {
            return NONE;
        }
        // days:缺失 = 全周;有值则必须是 DayOfWeek 的合法 3 字母短名子集
        List<String> days = List.of();
        if (raw.days != null && !raw.days.isEmpty()) {
            if (!isValidDays(raw.days)) {
                return NONE;
            }
            days = List.copyOf(raw.days);
        }
        return new MaintenanceWindow(s, e, days);
    }

    /// 完整校验:集合非空,且每一项都是合法 3 字母短名(MON..SUN)。
    private static boolean isValidDays(List<String> days) {
        for (String d : days) {
            if (!ALL_DAYS.contains(d)) {
                return false;
            }
        }
        return true;
    }
}
