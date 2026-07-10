package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition.EvalResult;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainExpiryAlertDefinitionTest {

    private static final Instant FROZEN = Instant.parse("2026-07-01T00:00:00Z");
    private static final long DAY_MS = 86_400_000L;
    private static final Clock CLOCK = Clock.fixed(FROZEN, ZoneOffset.UTC);

    private final DomainExpiryAlertDefinition def = new DomainExpiryAlertDefinition();

    private Site site(Long domainExpiresAt) {
        var s = new Site();
        s.setId(1L);
        s.setName("site");
        s.setUrl("https://site.example.com");
        s.setAvailabilityStatus(SiteStatus.UP);
        s.setDomainExpiresAt(domainExpiresAt);
        return s;
    }

    private EvalResult single(Set<EvalResult> set) {
        assertEquals(1, set.size(), "检测器应只返 1 条 EvalResult，实际: " + set);
        return set.iterator().next();
    }

    @Test
    void kind_isDomainExpiring() {
        assertEquals(AlertKind.DOMAIN_EXPIRING, def.kind());
    }

    @Test
    void domainNull_returnsEmpty() {
        var s = site(null);

        Set<EvalResult> result = def.eval(s, CLOCK);

        assertTrue(result.isEmpty(), "无域名到期信息的站点不适用域名维度");
    }

    @Test
    void domainExpired_returnsExpiredBucket() {
        long expiresAt = FROZEN.toEpochMilli() - 2 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertEquals("EXPIRED", er.bucket());
        assertTrue(er.message().contains("已过期 2 天"), "实际: " + er.message());
    }

    /// 域名阈值固定 15 天，10 天剩余 → W15
    @Test
    void domainWithin15Days_bucketIsW15() {
        long expiresAt = FROZEN.toEpochMilli() + 10 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertEquals("W15", er.bucket());
        assertTrue(er.message().contains("10 天后过期"), "实际: " + er.message());
    }

    @Test
    void domainBeyond15Days_returnsNormalBucket() {
        long expiresAt = FROZEN.toEpochMilli() + 60 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.NORMAL, er.status());
        assertEquals("NORMAL", er.bucket());
    }
}
