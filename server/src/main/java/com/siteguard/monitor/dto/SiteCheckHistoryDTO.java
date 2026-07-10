package com.siteguard.monitor.dto;

import com.siteguard.monitor.entity.CheckStatus;
import lombok.Data;

import java.io.Serializable;

/// 单条历史记录输出 DTO。
/// 暂未在 REST 接口直接暴露（接口侧只返回聚合），保留以备后续按站点详情使用。
@Data
public class SiteCheckHistoryDTO implements Serializable {
    private Long id;
    private Long siteId;
    private Long checkedAt;
    private CheckStatus status;
    private Integer httpStatus;
    private Integer responseMs;
    private String errorMessage;
}