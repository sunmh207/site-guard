package com.siteguard.monitor.dto;

import com.siteguard.monitor.jsoncondition.JsonAssertionConfigDTO;
import com.siteguard.monitor.probe.PathCheckType;

/// 站点自定义子路由检测规则 DTO。
///
/// API 使用结构化 assertionConfig；数据库由 codec 保存为 versioned JSON 文本。
/// last* 与 alertingSince 均为服务端只读状态，保存规则时会被强制清空。
public record SitePathRuleDTO(
        Long id,
        Long siteId,
        String path,
        Integer expectedHttpStatus,
        PathCheckType checkType,
        String expectedText,
        JsonAssertionConfigDTO assertionConfig,
        Long lastCheckedAt,
        Integer lastHttpStatus,
        Boolean lastTextMatched,
        Boolean lastJsonMatched,
        String lastJsonDetail,
        String lastErrorMessage,
        Long alertingSince
) {
    /// 兼容旧调用点；新增 JSON 字段默认 null，checkType=null 仍由 service 归一化为 HTTP_STATUS。
    public SitePathRuleDTO(Long id, Long siteId, String path, Integer expectedHttpStatus,
                           PathCheckType checkType, String expectedText,
                           Long lastCheckedAt, Integer lastHttpStatus, Boolean lastTextMatched,
                           String lastErrorMessage, Long alertingSince) {
        this(id, siteId, path, expectedHttpStatus, checkType, expectedText, null,
                lastCheckedAt, lastHttpStatus, lastTextMatched, null, null,
                lastErrorMessage, alertingSince);
    }
}
