package com.siteguard.monitor.dto;

import com.siteguard.monitor.jsoncondition.JsonAssertionConfigDTO;
import com.siteguard.monitor.probe.PathCheckType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/// 未保存规则的一次性测试请求；URL 始终从 path variable 对应的 Site 读取。
public record SitePathRuleTestRequest(
        @NotBlank String path,
        @NotNull Integer expectedHttpStatus,
        @NotNull PathCheckType checkType,
        String expectedText,
        JsonAssertionConfigDTO assertionConfig
) {}
