package com.siteguard.site.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// MaintenanceWindow.parse 边界测试。覆盖:
/// - null / 空 → NONE
/// - 非法 JSON → NONE
/// - start==end → NONE
/// - 非法 days 元素 → NONE
/// - 合法结构 → 解析成功(含跨日窗口、星期子集)
/// - 反序列化 → 重新序列化 round-trip 稳定
class MaintenanceWindowTest {

    @Test
    void parse_nullOrEmpty_returnsNONE() {
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse((String) null));
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse(""));
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("   "));
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("{}"));
    }

    @Test
    void parse_invalidJson_returnsNONE() {
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("not-json"));
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("[1,2,3]"));   // 数组不被接受(只能是对象)
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("\"foo\""));
    }

    @Test
    void parse_missingRequiredFields_returnsNONE() {
        // 缺 end
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("{\"start\":\"22:00\"}"));
        // 缺 start
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("{\"end\":\"08:00\"}"));
        // start / end 格式非法
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("{\"start\":\"25:00\",\"end\":\"08:00\"}"));
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("{\"start\":\"22:00\",\"end\":\"xx\"}"));
    }

    @Test
    void parse_startEqualsEnd_returnsNONE() {
        // start == end:二义性(0 长度或 24 小时),一律拒绝
        assertEquals(MaintenanceWindow.NONE, MaintenanceWindow.parse("{\"start\":\"08:00\",\"end\":\"08:00\"}"));
    }

    @Test
    void parse_invalidDays_returnsNONE() {
        // 含非法短名 FOO
        assertEquals(MaintenanceWindow.NONE,
                MaintenanceWindow.parse("{\"start\":\"22:00\",\"end\":\"08:00\",\"days\":[\"MON\",\"FOO\"]}"));
    }

    @Test
    void parse_validSameDayWindow_success() {
        var w = MaintenanceWindow.parse("{\"start\":\"09:00\",\"end\":\"18:00\"}");
        assertNotNull(w);
        assertTrue(!w.isEmpty());
        assertEquals("09:00", w.startTime().toString());
        assertEquals("18:00", w.endTime().toString());
        assertTrue(w.days().isEmpty(), "days 缺失 = 全周,为空列表");
    }

    @Test
    void parse_validCrossMidnightWindow_success() {
        var w = MaintenanceWindow.parse("{\"start\":\"22:00\",\"end\":\"08:00\",\"days\":[\"MON\",\"TUE\"]}");
        assertNotNull(w);
        assertEquals("22:00", w.startTime().toString());
        assertEquals("08:00", w.endTime().toString());
        assertEquals(List.of("MON", "TUE"), w.days());
    }

    @Test
    void json_roundTrip_stable() {
        // parse → json → parse:结果应语义相等
        var w = MaintenanceWindow.parse("{\"start\":\"22:00\",\"end\":\"08:00\",\"days\":[\"MON\",\"WED\",\"FRI\"]}");
        String json = MaintenanceWindow.json(w);
        assertNotNull(json);
        var w2 = MaintenanceWindow.parse(json);
        assertEquals(w, w2);
        // 保持用户输入顺序(round-trip 后不变)
        assertEquals(List.of("MON", "WED", "FRI"), w2.days());
    }

    @Test
    void json_none_returnsNull() {
        assertNull(MaintenanceWindow.json(MaintenanceWindow.NONE));
        assertNull(MaintenanceWindow.json(null));
    }
}
