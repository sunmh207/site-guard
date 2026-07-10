package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateId;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathCheckAlertSourceTest {

    @Mock private SiteCheckStateRepository stateRepo;
    @Mock private SitePathRuleRepository ruleRepo;

    @InjectMocks private PathCheckAlertSource src;

    private static Site site(long id, String url) {
        var s = new Site();
        s.setId(id);
        s.setName("site-" + id);
        s.setUrl(url);
        return s;
    }

    private static SiteCheckState state(long siteId, String bucket, long updatedAt) {
        return SiteCheckState.builder()
                .id(new SiteCheckStateId(siteId, "PATH_CHECK", bucket))
                .lastNotifiedAt(0L)
                .updatedAt(updatedAt)
                .build();
    }

    private static SitePathRule rule(long siteId, String path, int expected, Integer lastHttpStatus, String err) {
        var r = new SitePathRule();
        r.setId(System.nanoTime());
        r.setSiteId(siteId);
        r.setPath(path);
        r.setExpectedHttpStatus(expected);
        r.setLastHttpStatus(lastHttpStatus);
        r.setLastErrorMessage(err);
        r.setLastCheckedAt(1000L);
        return r;
    }

    @Test
    void kind_returnsPathCheck() {
        assertEquals(AlertKind.PATH_CHECK, src.kind());
    }

    @Test
    void multipleFailingPaths_emitOneAlertDtoPerPath() {
        var s = site(1L, "https://s.example");
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(
                state(1L, "/api/orders", 1000L),
                state(1L, "/api/payments", 1100L)));
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(
                rule(1L, "/api/orders", 200, 500, null),
                rule(1L, "/api/payments", 200, null, "连接拒绝")));

        var result = src.load(List.of(s));
        assertEquals(2, result.size());

        var orders = result.stream().filter(a -> a.getMessage().contains("/api/orders")).findFirst().orElseThrow();
        assertEquals(AlertStatus.ABNORMAL, orders.getStatus());
        assertEquals(1000L, orders.getDetectedAt());
        assertTrue(orders.getMessage().contains("500"));

        var payments = result.stream().filter(a -> a.getMessage().contains("/api/payments")).findFirst().orElseThrow();
        assertTrue(payments.getMessage().contains("连接拒绝"));
    }

    @Test
    void pausedSite_excluded() {
        var s = site(1L, "https://s.example");
        s.setPaused(true);
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(
                state(1L, "/api/orders", 1000L)));

        assertTrue(src.load(List.of(s)).isEmpty());
    }

    @Test
    void siteDeletedAfterStateWritten_excluded() {
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(
                state(1L, "/api/orders", 1000L)));
        assertTrue(src.load(List.of()).isEmpty());
    }

    @Test
    void ruleDeletedAfterStateWritten_useFallbackMessage() {
        var s = site(1L, "https://s.example");
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(
                state(1L, "/api/orders", 1000L)));
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of());   // rule gone

        var result = src.load(List.of(s));
        assertEquals(1, result.size());
        assertTrue(result.get(0).getMessage().contains("规则已删除"));
    }
}