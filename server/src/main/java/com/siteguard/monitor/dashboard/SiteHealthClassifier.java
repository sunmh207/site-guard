package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.dto.SiteHealthSummary;
import com.siteguard.site.entity.Site;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Dashboard 站点健康分桶器。
///
/// 输入：完整 Site 列表 + 当前活跃的 AlertDTO 列表（与下方告警面板同源）。
/// 输出：5 张卡片所需的全部统计（SiteHealthSummary）。
///
/// 优先级（一次只落一个桶）：暂停 > 异常 > 健康 > 待检测。
/// 不变性：healthy + abnormal + pending + paused == totalSites。
///
/// - 暂停：Site.paused == true
/// - 异常：alerts 中存在该 siteId 且 AlertStatus.ABNORMAL（任一告警源）
/// - 健康：Site.lastCheckedAt != null 且不在异常集
/// - 待检测：Site.lastCheckedAt == null 且不在异常集
@Service
public class SiteHealthClassifier {

    /// 一次性分桶：先构造 ABNORMAL 站点集合，再扫描 sites 一次，O(N+M)。
    ///
    /// 单一职责：只算桶，不感知时间窗口（avgResponseMs 仍由调用方补）。
    public SiteHealthSummary classify(List<Site> sites, List<AlertDTO> alerts) {
        Set<Long> abnormalSiteIds = alerts.stream()
                .filter(a -> a.getStatus() == AlertStatus.ABNORMAL)
                .map(AlertDTO::getSiteId)
                .collect(Collectors.toSet());

        long paused = 0, abnormal = 0, healthy = 0, pending = 0;
        for (Site s : sites) {
            if (s.isPaused()) {
                paused++;
            } else if (abnormalSiteIds.contains(s.getId())) {
                abnormal++;
            } else if (s.getLastCheckedAt() != null) {
                healthy++;
            } else {
                pending++;
            }
        }
        return new SiteHealthSummary(sites.size(), healthy, abnormal, pending, paused);
    }
}