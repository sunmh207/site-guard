package com.siteguard.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/// Dashboard 5 张卡片的站点健康分桶结果（SiteHealthClassifier 输出）。
///
/// 4 桶 + 总站点数，不变性：healthy + abnormal + pending + paused == totalSites。
/// 优先级（一次只落一个桶）：暂停 > 异常 > 健康 > 待检测。
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SiteHealthSummary implements Serializable {
    private long totalSites;
    /// 被探测过（lastCheckedAt != null）且无任何 ABNORMAL 告警
    private long healthyCount;
    /// 有任意 ABNORMAL 告警（availability / cert / domain / path 任一）
    private long abnormalCount;
    /// 从未被探测过（lastCheckedAt == null）
    private long pendingCount;
    /// 主动暂停监控（paused=true）
    private long pausedCount;
}