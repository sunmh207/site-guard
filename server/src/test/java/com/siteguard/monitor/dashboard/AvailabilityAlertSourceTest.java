package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateId;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
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
    SiteCheckStateRepository stateRepo;

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

    private SiteCheckState state(long siteId, String bucket, long updatedAt) {
        return SiteCheckState.builder()
                .id(new SiteCheckStateId(siteId, AlertKind.AVAILABILITY.name(), bucket))
                .lastNotifiedAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
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

    /// 连续失败尚未达到阈值时只有 DOWN 快照和失败 history，没有告警状态，Dashboard 不展示。
    @Test
    void noDownState_withDownSnapshotAndHistory_returnsEmpty() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(
                history(1, now, CheckStatus.DOWN, 500, 200, "down")));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of());

        List<AlertDTO> alerts = source.load(sites);

        assertTrue(alerts.isEmpty());
    }

    /// DOWN state 是展示真相；瞬时 site 快照即使已经变为 UP，也要等状态机删除 state 后再恢复。
    @Test
    void downState_emitsAbnormalAndUsesStateUpdatedAt() {
        var now = Instant.now().toEpochMilli();
        var stateUpdatedAt = now - 5_000L;
        var historyCheckedAt = now - 1_000L;
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.UP));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(
                history(1, historyCheckedAt, CheckStatus.DOWN, 500, 200, "down")));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "DOWN", stateUpdatedAt)));

        List<AlertDTO> alerts = source.load(sites);

        assertEquals(1, alerts.size());
        var alert = alerts.get(0);
        assertEquals(1L, alert.getSiteId());
        assertEquals(AlertKind.AVAILABILITY, alert.getKind());
        assertEquals(AlertStatus.ABNORMAL, alert.getStatus());
        assertEquals(stateUpdatedAt, alert.getDetectedAt());
        assertTrue(alert.getMessage().contains("HTTP 500"));
    }

    @Test
    void multipleHistoriesForSameSite_useLatestForMessage() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(
                history(1, now - 30_000L, CheckStatus.TIMEOUT, null, 5000, "timeout-A"),
                history(1, now - 20_000L, CheckStatus.DOWN, 500, 200, "down-B"),
                history(1, now - 10_000L, CheckStatus.ERROR, null, null, "boom-C")));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "DOWN", now - 25_000L)));

        List<AlertDTO> alerts = source.load(sites);

        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0).getMessage().contains("boom-C"));
    }

    @Test
    void timeoutHistory_enrichesMessage() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(
                history(1, now, CheckStatus.TIMEOUT, null, 5000, "timeout")));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "DOWN", now)));

        var alerts = source.load(sites);

        assertEquals(AlertStatus.ABNORMAL, alerts.get(0).getStatus());
        assertTrue(alerts.get(0).getMessage().contains("5000"));
    }

    @Test
    void errorHistory_enrichesMessage() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(
                history(1, now, CheckStatus.ERROR, null, null, "boom")));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "DOWN", now)));

        var alerts = source.load(sites);

        assertEquals(AlertStatus.ABNORMAL, alerts.get(0).getStatus());
        assertTrue(alerts.get(0).getMessage().contains("boom"));
    }

    @Test
    void downStateWithoutHistory_usesFallbackMessage() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of());
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "DOWN", now)));

        var alerts = source.load(sites);

        assertEquals(1, alerts.size());
        assertEquals("当前不可用", alerts.get(0).getMessage());
        assertEquals(now, alerts.get(0).getDetectedAt());
    }

    @Test
    void upState_isSkippedEvenWithFailureHistory() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.DOWN));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of(
                history(1, now, CheckStatus.DOWN, 500, 200, "old-down")));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "UP", now)));

        var alerts = source.load(sites);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void stateForDeletedSite_isSkipped() {
        var now = Instant.now().toEpochMilli();
        var sites = List.of(site(1, "官网", "https://a.com", SiteStatus.UP));
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of());
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(99, "DOWN", now)));

        var alerts = source.load(sites);

        assertTrue(alerts.isEmpty(), "state for deleted site must be skipped");
    }

    @Test
    void pausedSite_isSkippedFromAlerts() {
        var now = Instant.now().toEpochMilli();
        var pausedSite = site(1, "官网", "https://a.com", SiteStatus.DOWN);
        pausedSite.setPaused(true);
        when(historyRepo.findRecentIssues(any(Pageable.class))).thenReturn(List.of());
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                state(1, "DOWN", now)));

        var alerts = source.load(List.of(pausedSite));

        assertTrue(alerts.isEmpty(), "paused site must be excluded from availability alerts");
    }
}
