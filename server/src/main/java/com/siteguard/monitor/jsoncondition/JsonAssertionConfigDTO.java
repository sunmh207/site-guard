package com.siteguard.monitor.jsoncondition;

import java.util.List;

/// 可版本化的 JSON 条件配置。第一版仅接受 version=1。
public record JsonAssertionConfigDTO(
        int version,
        JsonConditionCombinator combinator,
        List<JsonConditionDTO> conditions
) {}
