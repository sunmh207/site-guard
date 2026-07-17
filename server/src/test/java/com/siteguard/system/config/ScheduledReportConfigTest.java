package com.siteguard.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// 验证配置 JSON 序列化 / 反序列化的 round-trip，保证能正确存入 system_config.config_value。
class ScheduledReportConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roundTrip_preservesAllFields() throws Exception {
        ScheduledReportConfig cfg = ScheduledReportConfig.builder()
                .enabled(true)
                .time("08:30")
                .build();

        String json = MAPPER.writeValueAsString(cfg);
        ScheduledReportConfig parsed = MAPPER.readValue(json, ScheduledReportConfig.class);

        assertEquals(true, parsed.getEnabled());
        assertEquals("08:30", parsed.getTime());
    }

    @Test
    void missingFields_defaultToNull() throws Exception {
        /// JSON 缺失字段时反序列化为 null；调度器通过 getOrDefault 覆盖默认值。
        ScheduledReportConfig parsed = MAPPER.readValue("{}", ScheduledReportConfig.class);

        assertNull(parsed.getEnabled());
        assertNull(parsed.getTime());
    }
}
