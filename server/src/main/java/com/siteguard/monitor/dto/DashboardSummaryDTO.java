package com.siteguard.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/// 仪表盘汇总：5 张卡片的数据来源。
///
/// 4 桶 + 总站点数；不变性：healthy + abnormal + pending + paused == totalSites。
/// 优先级（一次只落一个桶）：暂停 > 异常 > 健康 > 待检测。
///
/// - totalSites: 全部站点数
/// - healthyCount: paused=false ∧ lastCheckedAt!=null ∧ 无 ABNORMAL 告警
/// - abnormalCount: paused=false ∧ alerts 中存在 ABNORMAL（availability/cert/domain/path 任一）
/// - pendingCount: paused=false ∧ lastCheckedAt==null
/// - pausedCount: paused=true
/// - avgResponseMs: 近 1 小时所有 UP 探测的平均响应时间，无样本为 null（与本次分桶逻辑独立）
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardSummaryDTO implements Serializable {
    private long totalSites;
    private long healthyCount;
    private long abnormalCount;
    private long pendingCount;
    private long pausedCount;
    private Double avgResponseMs;
}