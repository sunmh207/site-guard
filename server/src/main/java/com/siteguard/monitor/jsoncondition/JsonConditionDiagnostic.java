package com.siteguard.monitor.jsoncondition;

/// 单条条件的评估诊断；actualValue/reason 均为安全截断后的展示文本，不含完整响应体。
public record JsonConditionDiagnostic(
        int index,
        String path,
        JsonConditionOperator operator,
        boolean matched,
        JsonActualType actualType,
        String actualValue,
        String expectedValue,
        String reason
) {}
