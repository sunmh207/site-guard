package com.siteguard.monitor.jsoncondition;

/// 单条 JSON 字段条件。
/// expectedValue 采用 String|null：数字操作符由服务端解析为 BigDecimal，文本操作符按原文比较，
/// 无值操作符必须传 null，避免 Object/JsonNode 进入稳定的 API 协议。
public record JsonConditionDTO(
        String path,
        JsonConditionOperator operator,
        String expectedValue
) {}
