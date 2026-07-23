package com.siteguard.monitor.dto;

import com.siteguard.monitor.entity.CheckStatus;

/// 单条子路由检测历史输出 DTO。
///
/// 镜像 SiteCheckHistoryDTO，增加 rule_id / path / text_matched 以支撑路径维度展示。
public record SitePathCheckHistoryDTO(
        Long id,
        Long siteId,
        Long ruleId,
        String path,
        Long checkedAt,
        CheckStatus status,
        Integer httpStatus,
        Boolean textMatched,
        String errorMessage
) {}
