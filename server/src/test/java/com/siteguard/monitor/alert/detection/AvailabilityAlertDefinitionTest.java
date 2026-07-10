package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition.EvalResult;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.system.config.ConsecutiveFailureConfig;
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
class AvailabilityAlertDefinitionTest {

    private static final Instant FROZEN = Instant.parse("2026-07-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FROZEN, ZoneOffset.UTC);

    @Mock
    ConfigService configService;

    private AvailabilityAlertDefinition defWith(ConsecutiveFailureConfig cfg) {
        org.mockito.Mockito.lenient()
                .when(configService.getOrDefault(
                        eq(ConfigKey.CONSECUTIVE_FAILURES_BEFORE_ALERT),
                        any(ConsecutiveFailureConfig.class)))
                .thenReturn(cfg);
        return new AvailabilityAlertDefinition(configService);
    }

    private AvailabilityAlertDefinition defWithDefault() {
        return defWith(new ConsecutiveFailureConfig());
    }

    private Site site(SiteStatus status) {
        var s = new Site();
        s.setId(1L);
        s.setName("site");
        s.setUrl("https://example.com");
        s.setAvailabilityStatus(status);
        return s;
    }

    private EvalResult single(Set<EvalResult> set) {
        assertEquals(1, set.size(), "检测器应只返 1 条 EvalResult，实际: " + set);
        return set.iterator().next();
    }

    @Test
    void kind_isAvailability() {
        var def = defWithDefault();
        assertEquals(AlertKind.AVAILABILITY, def.kind());
    }

    @Test
    void up_returnsNormalBucket() {
        var def = defWithDefault();
        var s = site(SiteStatus.UP);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.NORMAL, er.status());
        assertEquals("UP", er.bucket());
    }

    @Test
    void down_returnsAbnormalDownBucket() {
        var def = defWithDefault();
        var s = site(SiteStatus.DOWN);
        // counter >= threshold (1) → 触发 DOWN 异常
        s.setConsecutiveAvailabilityFailures(1);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertEquals("DOWN", er.bucket());
        // 检测器只描述"发生了什么"；站点名称由 service/listener 组合
        assertEquals("当前不可用", er.message());
    }

    /// 探测器消息不含站点名：站点上下文由 NotificationListener 统一拼装
    @Test
    void downMessage_doesNotContainSiteName() {
        var def = defWithDefault();
        var s = site(SiteStatus.DOWN);
        s.setName("官网");
        s.setConsecutiveAvailabilityFailures(1);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertTrue(!er.message().contains("官网"),
                "检测器消息不应含站点名，实际: " + er.message());
    }

    /// UNKNOWN = 尚未探测完成 → 按监控保守原则视为 DOWN 异常，避免"未探测即不告警"的盲区
    @Test
    void unknown_treatedAsAbnormal() {
        var def = defWithDefault();
        var s = site(SiteStatus.UNKNOWN);
        s.setConsecutiveAvailabilityFailures(1);

        Set<EvalResult> result = def.eval(s, CLOCK);

        EvalResult er = single(result);
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertEquals("DOWN", er.bucket());
    }

    /// 阈值判定：counter < threshold → 空集（未达阈值，不参与判定）
    @Test
    void evalReturnsAbnormalOnlyWhenCounterReachesThreshold() {
        var def = defWithDefault();  // 默认阈值 1

        var s = site(SiteStatus.DOWN);
        s.setConsecutiveAvailabilityFailures(0);  // counter=0, threshold=1

        var results = def.eval(s, CLOCK);
        assertTrue(results.isEmpty(),
                "counter < threshold 应返回空集，实际: " + results);
    }

    /// 阈值判定：counter >= threshold → 返回 DOWN 异常
    @Test
    void evalReturnsAbnormalWhenCounterMeetsThreshold() {
        var def = defWith(ConsecutiveFailureConfig.builder()
                .consecutiveFailuresBeforeAlert(3).build());

        var s = site(SiteStatus.DOWN);
        s.setConsecutiveAvailabilityFailures(3);  // counter=3 == threshold=3

        var results = def.eval(s, CLOCK);
        assertEquals(1, results.size());
        var r = results.iterator().next();
        assertEquals("DOWN", r.bucket());
        assertEquals(AlertStatus.ABNORMAL, r.status());
    }

    /// 站点级阈值覆盖优先于全局默认
    @Test
    void evalSiteOverrideTakesPrecedenceOverGlobalDefault() {
        // 全局默认 2
        var def = defWith(ConsecutiveFailureConfig.builder()
                .consecutiveFailuresBeforeAlert(2).build());

        var s = site(SiteStatus.DOWN);
        s.setConsecutiveAvailabilityFailures(4);  // counter=4
        s.setConsecutiveFailuresBeforeAlert(5);    // 站点级覆盖 threshold=5

        var results = def.eval(s, CLOCK);
        // counter(4) < override(5) → 空集
        assertTrue(results.isEmpty(),
                "counter < 站点级 override 应返回空集，实际: " + results);
    }

    /// UP 状态：counter 不参与阈值判定，永远返回 NORMAL UP
    @Test
    void evalReturnsUpOnStatusUp() {
        var def = defWithDefault();
        var s = site(SiteStatus.UP);
        s.setConsecutiveAvailabilityFailures(0);

        var results = def.eval(s, CLOCK);
        assertEquals(1, results.size());
        var r = results.iterator().next();
        assertEquals("UP", r.bucket());
        assertEquals(AlertStatus.NORMAL, r.status());
    }
}
