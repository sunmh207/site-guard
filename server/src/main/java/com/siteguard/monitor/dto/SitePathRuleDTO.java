package com.siteguard.monitor.dto;

/// 站点自定义子路由检测规则 DTO。
///
/// 字段分三组：
/// - 配置：id / siteId / path / expectedHttpStatus（用户编辑）
/// - 探测状态：lastCheckedAt / lastHttpStatus / lastErrorMessage（PathCheckProbe 写入）
/// - 告警状态：alertingSince（SitePathRuleServiceImpl.listBySite 时联查 site_check_state 填充，
///                          非 null 表示当前在告警，值为 state 行 updatedAt）
public record SitePathRuleDTO(
        Long id,
        Long siteId,
        String path,
        Integer expectedHttpStatus,
        Long lastCheckedAt,
        Integer lastHttpStatus,
        String lastErrorMessage,
        Long alertingSince
) {}