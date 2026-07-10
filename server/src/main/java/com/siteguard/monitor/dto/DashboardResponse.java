package com.siteguard.monitor.dto;

import com.siteguard.monitor.alert.AlertDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/// 仪表盘聚合响应：summary + recentAlerts 一次性返回。
///
/// 破坏性变更：旧字段 `recentIssues: List<RecentIssueDTO>` 替换为
/// `recentAlerts: List<AlertDTO>`，前端同步迁移。
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardResponse implements Serializable {
    private DashboardSummaryDTO summary;
    private List<AlertDTO> recentAlerts;
}