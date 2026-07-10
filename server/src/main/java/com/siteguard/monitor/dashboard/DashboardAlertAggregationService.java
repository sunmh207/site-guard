package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.DashboardSummaryDTO;
import com.siteguard.monitor.dto.SiteHealthSummary;
import com.siteguard.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/// 仪表盘聚合服务：一次性加载 sites + alerts，由 SiteHealthClassifier 算桶，
/// 最终拼装 DashboardResponse。
///
/// 设计：
/// - summary 由本服务自算（不再由 caller 传入），避免 caller 拿旧 sites 算 summary、
///   又用新 sites 算 alerts 的双源不一致
/// - alerts 排序：detectedAt 倒序（最新探测到的异常置顶），硬上限 ALERTS_CAP
/// - 单 source 异常隔离：每个 DashboardAlertSource 单独 try-catch，
///   一类告警源失败不影响其他源或整体面板
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardAlertAggregationService {

    /// 仪表盘告警条数上限
    public static final int ALERTS_CAP = 200;

    /// 排序：detectedAt 倒序（最新探测到的异常置顶）
    private static final Comparator<AlertDTO> CMP =
            Comparator.comparingLong(AlertDTO::getDetectedAt).reversed();

    private final SiteRepository siteRepo;
    private final SiteHealthClassifier classifier;
    private final List<DashboardAlertSource> sources;

    /// 一次性聚合：sites → alerts → classifier 分桶 → 拼装响应。
    public DashboardResponse aggregate() {
        var sites = siteRepo.findAll();

        // 先加载 alerts（带 site 上下文），再分桶（避免先分桶再加载 alerts 的双扫描）
        var alerts = sources.stream()
                .flatMap(s -> {
                    try {
                        return s.load(sites).stream();
                    } catch (RuntimeException e) {
                        log.warn("Alert source {} failed: {}", s.kind(), e.getMessage());
                        return Stream.empty();
                    }
                })
                .sorted(CMP)
                .limit(ALERTS_CAP)
                .toList();

        var summary = classifier.classify(sites, alerts);
        return new DashboardResponse(toDto(summary), alerts);
    }

    /// 把分桶结果转成对外 DTO；独立方法便于单测断言。
    /// avgResponseMs 留给 caller（SiteCheckServiceImpl）补——本服务不感知时间窗口。
    private static DashboardSummaryDTO toDto(SiteHealthSummary h) {
        return new DashboardSummaryDTO(
                h.getTotalSites(),
                h.getHealthyCount(),
                h.getAbnormalCount(),
                h.getPendingCount(),
                h.getPausedCount(),
                null);
    }
}