package com.siteguard.monitor.dto;

import com.siteguard.monitor.entity.CheckStatus;

/// 单条子路由检测历史输出 DTO。
/// status 表示请求是否完成；业务判定分别由 httpStatus/textMatched/jsonMatched 展示。
public record SitePathCheckHistoryDTO(
        Long id,
        Long siteId,
        Long ruleId,
        String path,
        Long checkedAt,
        CheckStatus status,
        Integer httpStatus,
        Boolean textMatched,
        Boolean jsonMatched,
        String jsonDetail,
        String errorMessage
) {
    /// 兼容旧的 HTTP_STATUS/KEYWORD 测试与调用点。
    public SitePathCheckHistoryDTO(Long id, Long siteId, Long ruleId, String path,
                                   Long checkedAt, CheckStatus status, Integer httpStatus,
                                   Boolean textMatched, String errorMessage) {
        this(id, siteId, ruleId, path, checkedAt, status, httpStatus, textMatched,
                null, null, errorMessage);
    }
}
