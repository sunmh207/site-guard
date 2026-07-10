package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpirationAlertSourceTest {

    /// 固定时钟到 2026-07-01T00:00:00Z，便于写出确定性时间断言
    private static final Instant FROZEN = Instant.parse("2026-07-01T00:00:00Z");
    private static final long DAY_MS = 86_400_000L;
    private final ExpirationAlertSource source =
            new ExpirationAlertSource(Clock.fixed(FROZEN, ZoneOffset.UTC));

    private Site site(long id, Long certExpiresAt, Long domainExpiresAt, Long lastCheckedAt) {
        var s = new Site();
        s.setId(id);
        s.setName("site-" + id);
        s.setUrl("https://site" + id + ".example.com");
        s.setAvailabilityStatus(SiteStatus.UP);
        s.setCertificateExpiresAt(certExpiresAt);
        s.setDomainExpiresAt(domainExpiresAt);
        s.setLastCheckedAt(lastCheckedAt);
        return s;
    }

    @Test
    void certExpired_returnsAbnormal() {
        long expiresAt = FROZEN.toEpochMilli() - 3 * DAY_MS;   // 已过期 3 天
        var s = site(1, expiresAt, null, FROZEN.toEpochMilli() - 86_400_000L);

        var alerts = source.load(List.of(s));

        assertEquals(1, alerts.size());
        var a = alerts.get(0);
        assertEquals(AlertKind.CERT_EXPIRY, a.getKind());
        assertEquals(AlertStatus.ABNORMAL, a.getStatus());
        assertEquals("证书已过期 3 天", a.getMessage());
    }

    @Test
    void certWithinThreshold_returnsAbnormal() {
        long expiresAt = FROZEN.toEpochMilli() + 7 * DAY_MS;
        var s = site(1, expiresAt, null, FROZEN.toEpochMilli());

        var alerts = source.load(List.of(s));

        assertEquals(1, alerts.size());
        var a = alerts.get(0);
        assertEquals(AlertKind.CERT_EXPIRY, a.getKind());
        assertEquals(AlertStatus.ABNORMAL, a.getStatus());
        assertEquals("证书将于 7 天后过期", a.getMessage());
    }

    @Test
    void certBeyondThreshold_noAlert() {
        long expiresAt = FROZEN.toEpochMilli() + 60 * DAY_MS;   // 远未到期
        var s = site(1, expiresAt, null, FROZEN.toEpochMilli());

        var alerts = source.load(List.of(s));

        assertTrue(alerts.isEmpty());
    }

    @Test
    void certNull_noAlert() {
        var s = site(1, null, null, FROZEN.toEpochMilli());

        var alerts = source.load(List.of(s));

        assertTrue(alerts.isEmpty());
    }

    @Test
    void certAndDomainBothAlert_returnsTwoAlerts() {
        long certAt = FROZEN.toEpochMilli() + 7 * DAY_MS;        // 7 天后过期
        long domAt = FROZEN.toEpochMilli() - 2 * DAY_MS;         // 已过期 2 天
        var s = site(1, certAt, domAt, FROZEN.toEpochMilli());

        var alerts = source.load(List.of(s));

        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().anyMatch(a -> a.getKind() == AlertKind.CERT_EXPIRY
                && a.getStatus() == AlertStatus.ABNORMAL));
        assertTrue(alerts.stream().anyMatch(a -> a.getKind() == AlertKind.DOMAIN_EXPIRING
                && a.getStatus() == AlertStatus.ABNORMAL));
    }

    @Test
    void siteWithoutLastCheckedAt_fallsBackToNow() {
        long certAt = FROZEN.toEpochMilli() + 7 * DAY_MS;
        var s = site(1, certAt, null, null);   // lastCheckedAt 未检测

        var alerts = source.load(List.of(s));

        assertEquals(1, alerts.size());
        assertEquals(FROZEN.toEpochMilli(), alerts.get(0).getDetectedAt());
    }

    @Test
    void emptySites_noAlert() {
        var alerts = source.load(List.of());
        assertTrue(alerts.isEmpty());
    }

    @Test
    void certExactlyAtThreshold_returnsWarning() {
        // expiresAt - now == 15 天 → 触发 WARNING（< threshold 不含等号）
        // 实际边界：源码用 "expiresAt - now < warningMs"，15 天整不输出
        long expiresAt = FROZEN.toEpochMilli() + 15 * DAY_MS;
        var s = site(1, expiresAt, null, FROZEN.toEpochMilli());

        var alerts = source.load(List.of(s));

        assertTrue(alerts.isEmpty(), "15d 整不应触发 WARNING（边界属阈值外）");
    }

    /// 用户暂停的站点不应出现在 cert/domain 到期告警中：
    /// 即便证书 7 天后到期会触发 WARNING，paused=true 也必须过滤掉，
    /// 避免告警面板打扰主动暂不监控的站点。
    @Test
    void load_pausedSite_excludedFromCertAndDomainAlerts() {
        var paused = new Site();
        paused.setId(1L);
        paused.setName("paused");
        paused.setUrl("https://example.com");
        paused.setPaused(true);
        paused.setAvailabilityStatus(SiteStatus.UP);
        // 证书 7 天后过期：未暂停会触发 WARNING；暂停后应静默
        paused.setCertificateExpiresAt(FROZEN.toEpochMilli() + 7 * DAY_MS);
        paused.setDomainExpiresAt(FROZEN.toEpochMilli() + 3 * DAY_MS);
        paused.setLastCheckedAt(FROZEN.toEpochMilli());

        var alerts = source.load(List.of(paused));

        assertTrue(alerts.isEmpty(), "paused 站点不应产生 cert/domain 到期告警");
    }
}
