package com.siteguard.monitor.jsoncondition;

import java.util.List;

/// 一次 JSON 条件评估结果。
public record JsonConditionEvaluation(
        boolean parseSucceeded,
        boolean matched,
        List<JsonConditionDiagnostic> conditions,
        String detail
) {}
