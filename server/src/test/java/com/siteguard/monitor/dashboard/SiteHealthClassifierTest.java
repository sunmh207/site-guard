package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.dto.SiteHealthSummary;
import com.siteguard.site.entity.Site;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// SiteHealthClassifier 单测：纯 Java，无 Spring context。
///
/// 覆盖 4 个桶的所有转移路径 + 不变性断言。
class SiteHealthClassifierTest {

    private final SiteHealthClassifier classifier = new SiteHealthClassifier();

    /// 构造最小 Site；只填分桶所需字段（id / paused / lastCheckedAt）
    private static Site site(long id, boolean paused, Long lastCheckedAt) {
        var s = new Site();
        s.setId(id);
        s.setPaused(paused);
        s.setLastCheckedAt(lastCheckedAt);
        return s;
    }

    private static AlertDTO alert(long siteId, AlertKind kind, AlertStatus status) {
        var a = new AlertDTO();
        a.setSiteId(siteId);
        a.setKind(kind);
        a.setStatus(status);
        a.setDetectedAt(0L);
        a.setMessage("test");
        return a;
    }

    @Test
    void emptyInput_allZero() {
        var r = classifier.classify(List.of(), List.of());
        assertEquals(0L, r.getTotalSites());
        assertEquals(0L, r.getHealthyCount());
        assertEquals(0L, r.getAbnormalCount());
        assertEquals(0L, r.getPendingCount());
        assertEquals(0L, r.getPausedCount());
    }

    @Test
    void onePerBucket_eachLandsInCorrectBucket() {
        // site1 暂停 / site2 异常 / site3 健康 / site4 待检测
        var sites = List.of(
                site(1, true, 1000L),
                site(2, false, 1000L),
                site(3, false, 1000L),
                site(4, false, null));
        var alerts = List.of(alert(2, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(4L, r.getTotalSites());
        assertEquals(1L, r.getPausedCount());
        assertEquals(1L, r.getAbnormalCount());
        assertEquals(1L, r.getHealthyCount());
        assertEquals(1L, r.getPendingCount());
    }

    @Test
    void pausedSiteWithAbnormalAlert_countsAsPaused_notAbnormal() {
        // 暂停站点即便有 ABNORMAL 告警，按优先级归"暂停"——避免卡片双计
        var sites = List.of(site(1, true, 1000L));
        var alerts = List.of(alert(1, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(1L, r.getPausedCount());
        assertEquals(0L, r.getAbnormalCount());
    }

    @Test
    void pendingSiteWithAbnormalAlert_countsAsAbnormal_notPending() {
        // lastCheckedAt=null 但有 ABNORMAL → 异常（异常优先级高于待检测）
        var sites = List.of(site(1, false, null));
        var alerts = List.of(alert(1, AlertKind.CERT_EXPIRY, AlertStatus.ABNORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(1L, r.getAbnormalCount());
        assertEquals(0L, r.getPendingCount());
    }

    @Test
    void normalAlert_doesNotCountAsAbnormal() {
        var sites = List.of(site(1, false, 1000L));
        var alerts = List.of(alert(1, AlertKind.AVAILABILITY, AlertStatus.NORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(1L, r.getHealthyCount());
        assertEquals(0L, r.getAbnormalCount());
    }

    @Test
    void multipleAbnormalAlertsForSameSite_dedupBySiteId() {
        // 同一 site 多条 ABNORMAL 仍只计 1 个异常 site
        var sites = List.of(site(1, false, 1000L));
        var alerts = List.of(
                alert(1, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL),
                alert(1, AlertKind.CERT_EXPIRY, AlertStatus.ABNORMAL),
                alert(1, AlertKind.PATH_CHECK, AlertStatus.ABNORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(1L, r.getAbnormalCount());
    }

    @Test
    void invariants_holdForMixedInputs() {
        // 构造一组混合输入：4 桶都有
        var sites = List.of(
                site(1, true, 1000L),                                        // 暂停
                site(2, false, 1000L),                                       // 异常
                site(3, false, 1000L),                                       // 健康
                site(4, false, null),                                        // 待检测
                site(5, true, null),                                         // 暂停
                site(6, false, 1000L));                                      // 异常（site6 也有 ABNORMAL）
        var alerts = List.of(
                alert(2, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL),
                alert(6, AlertKind.DOMAIN_EXPIRING, AlertStatus.ABNORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(6L, r.getTotalSites());
        assertEquals(2L, r.getPausedCount(), "site1+site5");
        assertEquals(2L, r.getAbnormalCount(), "site2+site6");
        assertEquals(1L, r.getHealthyCount(), "site3");
        assertEquals(1L, r.getPendingCount(), "site4");
        // 不变性
        assertEquals(r.getTotalSites(),
                r.getHealthyCount() + r.getAbnormalCount() + r.getPendingCount() + r.getPausedCount());
    }

    @Test
    void pausedSiteWithNullLastChecked_countsAsPaused() {
        // 暂停 + lastCheckedAt=null → 仍归"暂停"
        var sites = List.of(site(1, true, null));

        var r = classifier.classify(sites, List.of());

        assertEquals(1L, r.getPausedCount());
        assertEquals(0L, r.getPendingCount());
    }

    @Test
    void alertsForUnknownSite_ignored() {
        // alerts 里有 siteId=999，但 sites 没有 → 不影响分桶
        var sites = List.of(site(1, false, 1000L));
        var alerts = List.of(alert(999, AlertKind.AVAILABILITY, AlertStatus.ABNORMAL));

        var r = classifier.classify(sites, alerts);

        assertEquals(1L, r.getHealthyCount());
        assertEquals(0L, r.getAbnormalCount());
    }
}