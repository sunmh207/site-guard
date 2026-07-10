package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SiteCheckHistory;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityAlertSourceTest {

    @Mock
    SiteCheckHistoryRepository historyRepo;

    @InjectMocks
    AvailabilityAlertSource source;

    private Site site(long id, String name, String url, SiteStatus status) {
        var s = new Site();
        s.setId(id);
        s.setName(name);
        s.setUrl(url);
        s.setAvailabilityStatus(status);
        return s;
    }

    private SiteCheckHistory history(long siteId, long checkedAt, CheckStatus status,
                                     Integer httpStatus, Integer responseMs, String error) {
        var h = new SiteCheckHistory();
        h.setSiteId(siteId);
        h.setCheckedAt(checkedAt);
        h.setStatus(status);
        h.setHttpStatus(httpStatus);
        h.setResponseMs(responseMs);
        h.setErrorMessage(error);
        return h;
    }

    @Test
    void multipleStatusesForSameSite_pickLatestOnly() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        // repo already dedupes by (site, status), so we get one per status
        var h1 = history(1, now - 30_000L, CheckStatus.TIMEOUT, null, 5000, "timeout-A");
        var h2 = history(1, now - 20_000L, CheckStatus.DOWN,    500,  200,  "down-B");
        var h3 = history(1, now - 10_000L, CheckStatus.ERROR,  null, null, "boom-C");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h1, h2, h3));

        List<AlertDTO> alerts = source.load(sites);

        // one site → one AVAILABILITY alert, picked by max checkedAt (= h3, ERROR)
        assertEquals(1, alerts.size());
        var a = alerts.get(0);
        assertEquals(1L, a.getSiteId());
        assertEquals(AlertKind.AVAILABILITY, a.getKind());
        assertEquals(now - 10_000L, a.getDetectedAt());
        assertTrue(a.getMessage().contains("boom-C"));
    }

    @Test
    void downMapsToAbnormal() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        var h = history(1, now, CheckStatus.DOWN, 500, 200, "down");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h));

        var alerts = source.load(sites);

        assertEquals(AlertStatus.ABNORMAL, alerts.get(0).getStatus());
    }

    @Test
    void timeoutMapsToAbnormal() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        var h = history(1, now, CheckStatus.TIMEOUT, null, 5000, "timeout");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h));

        var alerts = source.load(sites);

        assertEquals(AlertStatus.ABNORMAL, alerts.get(0).getStatus());
        assertTrue(alerts.get(0).getMessage().contains("5000"));
    }

    @Test
    void errorMapsToAbnormal() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        var h = history(1, now, CheckStatus.ERROR, null, null, "boom");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h));

        var alerts = source.load(sites);

        assertEquals(AlertStatus.ABNORMAL, alerts.get(0).getStatus());
        assertTrue(alerts.get(0).getMessage().contains("boom"));
    }

    @Test
    void emptyHistory_returnsEmptyList() {
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.UP));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of());

        var alerts = source.load(sites);

        assertEquals(0, alerts.size());
    }

    @Test
    void historyForDeletedSite_isSkipped() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.UP)); // only site id=1 exists
        var h = history(99, now, CheckStatus.DOWN, 500, 200, "ghost");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h));

        var alerts = source.load(sites);

        assertEquals(0, alerts.size(), "history for deleted site must be skipped");
    }

    /// 已恢复站点：history 里残留旧 DOWN 行，但 site 当前快照已是 UP → 不应列入告警
    /// （列表语义 = 当前存在的异常，非发生过的异常）
    @Test
    void recoveredSite_isSkipped() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.UP));
        // history 里有 10 分钟前的 DOWN 行，但 site 当前快照是 UP（已恢复）
        var h = history(1, now - 600_000L, CheckStatus.DOWN, 500, 200, "recovered-down");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h));

        var alerts = source.load(sites);

        assertEquals(0, alerts.size(), "site currently UP must not appear even with old non-UP history");
    }

    /// 暂停站点：用户主动暂停后，T9 已让扫描层不再写入新 history；
    /// 但旧 history 行可能仍存在 → 告警面板不应再展示这些站点的告警。
    @Test
    void pausedSite_isSkippedFromAlerts() {
        var now = Instant.now().toEpochMilli();
        var pausedSite = site(1, "官网", "https://a.com", SiteStatus.DOWN);
        pausedSite.setPaused(true);   // 用户已暂停
        var sites = List.of(pausedSite);
        // history 里有最近一条非 UP 状态（扫描层在 T9 之后不应再产生，但旧行还在）
        var h = history(1, now, CheckStatus.DOWN, 500, 200, "old-down");
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(h));

        var alerts = source.load(sites);

        assertEquals(0, alerts.size(), "paused site must be excluded from availability alerts");
    }
}
