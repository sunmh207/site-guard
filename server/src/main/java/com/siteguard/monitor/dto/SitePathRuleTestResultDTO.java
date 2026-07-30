package com.siteguard.monitor.dto;

import com.siteguard.monitor.jsoncondition.JsonConditionDiagnostic;

import java.util.List;

/// 子路由规则一次性测试结果；不包含响应体，只返回受限诊断信息。
public record SitePathRuleTestResultDTO(
        boolean requestCompleted,
        Integer httpStatus,
        boolean httpStatusMatched,
        Boolean bodyParsed,
        Boolean jsonMatched,
        Boolean textMatched,
        boolean healthy,
        String summary,
        List<JsonConditionDiagnostic> conditions,
        String errorMessage
) {}
