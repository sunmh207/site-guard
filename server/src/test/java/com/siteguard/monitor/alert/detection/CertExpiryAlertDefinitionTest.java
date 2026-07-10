package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition.EvalResult;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.system.config.CertAlertConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertExpiryAlertDefinitionTest {

    private static final Instant FROZEN = Instant.parse("2026-07-01T00:00:00Z");
    private static final long DAY_MS = 86_400_000L;
    private static final Clock CLOCK = Clock.fixed(FROZEN, ZoneOffset.UTC);

    @Mock
    ConfigService configService;

    private CertExpiryAlertDefinition defWith(int[] warningDays) {
        org.mockito.Mockito.lenient()
                .when(configService.getOrDefault(eq(ConfigKey.CERT_ALERT), any(CertAlertConfig.class)))
                .thenReturn(CertAlertConfig.builder().warningDays(warningDays).build());
        return new CertExpiryAlertDefinition(configService);
    }

    private Site site(Long certExpiresAt) {
        var s = new Site();
        s.setId(1L);
        s.setName("site");
        s.setUrl("https://site.example.com");
        s.setAvailabilityStatus(SiteStatus.UP);
        s.setCertificateExpiresAt(certExpiresAt);
        return s;
    }

    private EvalResult single(Set<EvalResult> set) {
        assertEquals(1, set.size(), "检测器应只返 1 条 EvalResult，实际: " + set);
        return set.iterator().next();
    }

    @Test
    void kind_isCertExpiry() {
        var def = defWith(new int[]{14, 7, 3});
        assertEquals(AlertKind.CERT_EXPIRY, def.kind());
    }

    @Test
    void certNull_returnsEmpty() {
        var def = defWith(new int[]{14, 7, 3});
        var s = site(null);

        Set<EvalResult> result = def.eval(s, CLOCK);

        assertTrue(result.isEmpty(), "无证书的站点不适用证书维度");
    }

    @Test
    void certExpired_returnsExpiredBucket() {
        var def = defWith(new int[]{14, 7, 3});
        long expiresAt = FROZEN.toEpochMilli() - 3 * DAY_MS;   // 已过期 3 天
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertEquals("EXPIRED", er.bucket());
        assertTrue(er.message().contains("已过期 3 天"), "实际: " + er.message());
    }

    /// 13 天剩余：14 天阈值匹配（13 < 14），但 7/3 不匹配
    /// 升序遍历确保落入最小阈值（最严重档位） → W14
    @Test
    void certWithin14Days_bucketIsW14() {
        var def = defWith(new int[]{14, 7, 3});
        long expiresAt = FROZEN.toEpochMilli() + 13 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertEquals("W14", er.bucket());
        assertTrue(er.message().contains("13 天后过期"), "实际: " + er.message());
    }

    @Test
    void certWithin7Days_bucketIsW7() {
        var def = defWith(new int[]{14, 7, 3});
        long expiresAt = FROZEN.toEpochMilli() + 5 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals("W7", er.bucket());
        assertTrue(er.message().contains("5 天后过期"), "实际: " + er.message());
    }

    @Test
    void certWithin3Days_bucketIsW3() {
        var def = defWith(new int[]{14, 7, 3});
        long expiresAt = FROZEN.toEpochMilli() + 2 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals("W3", er.bucket());
        assertTrue(er.message().contains("2 天后过期"), "实际: " + er.message());
    }

    @Test
    void certBeyondAllThresholds_returnsNormalBucket() {
        var def = defWith(new int[]{14, 7, 3});
        long expiresAt = FROZEN.toEpochMilli() + 60 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.NORMAL, er.status());
        assertEquals("NORMAL", er.bucket());
    }

    /// 阈值排序不影响结果：[3, 14, 7] 与 [14, 7, 3] 行为应一致
    @Test
    void unsortedWarningDays_behaveIdentically() {
        var def = defWith(new int[]{3, 14, 7});
        long expiresAt = FROZEN.toEpochMilli() + 5 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        assertEquals("W7", single(result).bucket());
    }

    /// 用户配置的 [30, 14, 7]：13 天应落入 W14（30 不命中、14 命中）
    @Test
    void customThresholds_areHonored() {
        var def = defWith(new int[]{30, 14, 7});
        long expiresAt = FROZEN.toEpochMilli() + 13 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        assertEquals("W14", single(result).bucket());
    }

    /// ConfigService 抛异常时退回默认阈值 [14, 7, 3]，避免单点故障拖垮告警系统
    @Test
    void configServiceThrows_fallsBackToDefaults() {
        when(configService.getOrDefault(eq(ConfigKey.CERT_ALERT), any(CertAlertConfig.class)))
                .thenThrow(new RuntimeException("DB down"));
        var def = new CertExpiryAlertDefinition(configService);
        long expiresAt = FROZEN.toEpochMilli() + 5 * DAY_MS;
        var s = site(expiresAt);

        Set<EvalResult> result = def.eval(s, CLOCK);

        assertEquals("W7", single(result).bucket());
    }
}
