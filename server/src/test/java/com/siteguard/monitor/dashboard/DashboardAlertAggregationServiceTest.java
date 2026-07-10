package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAlertAggregationServiceTest {

    @Mock SiteRepository siteRepo;

    /// 用一个静态 stub source 简化测试：构造时直接给返回的 list
    private static class StubSource implements DashboardAlertSource {
        private final AlertKind kind;
        private final List<AlertDTO> alerts;
        StubSource(AlertKind kind, List<AlertDTO> alerts) {
            this.kind = kind;
            this.alerts = alerts;
        }
        @Override public AlertKind kind() { return kind; }
        @Override public List<AlertDTO> load(List<Site> allSites) { return alerts; }
    }

    /// 抛出异常的 source：用来验证单源隔离
    private static class ThrowingSource implements DashboardAlertSource {
        private final AlertKind kind;
        ThrowingSource(AlertKind kind) { this.kind = kind; }
        @Override public AlertKind kind() { return kind; }
        @Override public List<AlertDTO> load(List<Site> allSites) {
            throw new RuntimeException("boom");
        }
    }

    private static AlertDTO alert(long siteId, AlertKind kind, AlertStatus status, long detectedAt) {
        var a = new AlertDTO();
        a.setSiteId(siteId);
        a.setKind(kind);
        a.setStatus(status);
        a.setDetectedAt(detectedAt);
        a.setMessage(kind + "@" + detectedAt);
        return a;
    }

    /// 构造最小 Site：仅填 classifier 所需字段
    private static Site site(long id, boolean paused, Long lastCheckedAt) {
        var s = new Site();
        s.setId(id);
        s.setPaused(paused);
        s.setLastCheckedAt(lastCheckedAt);
        return s;
    }

    private DashboardAlertAggregationService buildService(List<DashboardAlertSource> sources) {
        return new DashboardAlertAggregationService(siteRepo, new SiteHealthClassifier(), sources);
    }

    @Test
    void mergesMultipleSources_sortsByDetectedAtDesc_capsAt20() {
        when(siteRepo.findAll()).thenReturn(List.of());

        // 三个不同 kind 与 detectedAt 的 ABNORMAL 条目；
        // 验证排序只看 detectedAt 倒序（不再有 severity 维度）。
        var avail = new StubSource(AlertKind.AVAILABILITY, List.of(
                alert(1, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, 100),
                alert(2, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, 50)));
        var cert = new StubSource(AlertKind.CERT_EXPIRY, List.of(
                alert(3, AlertKind.CERT_EXPIRY, AlertStatus.ABNORMAL, 300)));

        var response = buildService(List.of(avail, cert)).aggregate();

        var alerts = response.getRecentAlerts();
        assertEquals(3, alerts.size());
        // detectedAt 倒序：300 → 100 → 50
        assertEquals(300L, alerts.get(0).getDetectedAt());
        assertEquals(100L, alerts.get(1).getDetectedAt());
        assertEquals(50L,  alerts.get(2).getDetectedAt());
    }

    @Test
    void capTo200() {
        // 实际 ALERTS_CAP = 200：构造 250 条 alerts，断言截断到 200 + detectedAt DESC 头部
        when(siteRepo.findAll()).thenReturn(List.of());

        List<AlertDTO> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            many.add(alert(i, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, i));
        }
        var src = new StubSource(AlertKind.AVAILABILITY, many);

        var response = buildService(List.of(src)).aggregate();

        assertEquals(200, response.getRecentAlerts().size());
        // cap 内是 detectedAt DESC 最大 200 个：detectedAt=249 排首位
        assertEquals(249L, response.getRecentAlerts().get(0).getSiteId());
    }

    @Test
    void singleSourceThrowing_doesNotBreakOthers() {
        when(siteRepo.findAll()).thenReturn(List.of());

        var good = new StubSource(AlertKind.AVAILABILITY, List.of(
                alert(1, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, 1)));
        var bad = new ThrowingSource(AlertKind.CERT_EXPIRY);

        var response = buildService(List.of(good, bad)).aggregate();

        assertEquals(1, response.getRecentAlerts().size());
        assertEquals(AlertKind.AVAILABILITY, response.getRecentAlerts().get(0).getKind());
    }

    @Test
    void noSources_returnsEmpty() {
        when(siteRepo.findAll()).thenReturn(List.of());

        var response = buildService(List.of()).aggregate();

        assertEquals(0, response.getRecentAlerts().size());
    }

    @Test
    void aggregate_summaryMatchesClassifier_andAvgIsNull() {
        // 4 个 site：1 暂停、1 异常、1 健康、1 待检测
        when(siteRepo.findAll()).thenReturn(List.of(
                site(1, true, 1000L),
                site(2, false, 1000L),
                site(3, false, 1000L),
                site(4, false, null)));

        var src = new StubSource(AlertKind.AVAILABILITY, List.of(
                alert(2, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, 1)));

        var response = buildService(List.of(src)).aggregate();
        var summary = response.getSummary();

        assertEquals(4L, summary.getTotalSites());
        assertEquals(1L, summary.getPausedCount());
        assertEquals(1L, summary.getAbnormalCount());
        assertEquals(1L, summary.getHealthyCount());
        assertEquals(1L, summary.getPendingCount());
        // avgResponseMs 不在本服务计算，留给 caller 补
        assertEquals(null, summary.getAvgResponseMs());
    }

    @Test
    void aggregate_pausedSiteWithAbnormalAlert_countedAsPausedOnly() {
        // 暂停+ABNORMAL 不重复计数
        when(siteRepo.findAll()).thenReturn(List.of(site(1, true, 1000L)));

        var src = new StubSource(AlertKind.AVAILABILITY, List.of(
                alert(1, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL, 1)));

        var response = buildService(List.of(src)).aggregate();
        var s = response.getSummary();

        assertEquals(1L, s.getTotalSites());
        assertEquals(1L, s.getPausedCount());
        assertEquals(0L, s.getAbnormalCount());
    }
}