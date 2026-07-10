package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition.EvalResult;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.system.config.ConsecutiveFailureConfig;
import com.siteguard.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathCheckAlertDefinitionTest {

    @Mock
    private SitePathRuleRepository ruleRepo;

    @Mock
    private ConfigService configService;

    @InjectMocks
    private PathCheckAlertDefinition def;

    private Site site(long id) {
        var s = new Site();
        s.setId(id);
        return s;
    }

    private SitePathRule rule(long id, String path, int expected, Integer lastHttpStatus, Long lastCheckedAt) {
        return rule(id, path, expected, lastHttpStatus, lastCheckedAt, 0);
    }

    private SitePathRule rule(long id, String path, int expected, Integer lastHttpStatus, Long lastCheckedAt, int consecutiveFailures) {
        var r = new SitePathRule();
        r.setId(id);
        r.setSiteId(1L);
        r.setPath(path);
        r.setExpectedHttpStatus(expected);
        r.setLastHttpStatus(lastHttpStatus);
        r.setLastCheckedAt(lastCheckedAt);
        r.setLastErrorMessage(null);
        r.setConsecutiveFailures(consecutiveFailures);
        return r;
    }

    @Test
    void kind_returnsPathCheck() {
        assertEquals(AlertKind.PATH_CHECK, def.kind());
    }

    @Test
    void noRules_returnsEmpty() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of());
        assertTrue(def.eval(site(1L), Clock.systemUTC()).isEmpty());
    }

    @Test
    void allPassing_returnsEmpty() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(
                rule(10L, "/api/a", 200, 200, 1000L),
                rule(11L, "/api/b", 200, 200, 1000L)));
        assertTrue(def.eval(site(1L), Clock.systemUTC()).isEmpty());
    }

    @Test
    void singleFailure_returnsOneEvalResultWithPathKey() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(
                rule(10L, "/api/a", 200, 200, 1000L),
                rule(11L, "/api/b", 200, 500, 1000L, 1)));
        Set<EvalResult> result = def.eval(site(1L), Clock.systemUTC());
        assertEquals(1, result.size());
        EvalResult er = result.iterator().next();
        assertEquals("/api/b", er.bucket());
        assertEquals(AlertStatus.ABNORMAL, er.status());
        assertTrue(er.message().contains("/api/b"));
        assertTrue(er.message().contains("500"));
    }

    @Test
    void multipleFailures_returnsAllAsEvalResults() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(
                rule(10L, "/api/a", 200, 500, 1000L, 1),
                rule(11L, "/api/b", 200, 404, 1000L, 1),
                rule(12L, "/api/c", 200, 200, 1000L)));
        Set<EvalResult> result = def.eval(site(1L), Clock.systemUTC());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "/api/a".equals(e.bucket())));
        assertTrue(result.stream().anyMatch(e -> "/api/b".equals(e.bucket())));
    }

    @Test
    void lastHttpStatusNull_treatedAsFailing() {
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(
                rule(10L, "/api/a", 200, null, 1000L, 1)));
        Set<EvalResult> result = def.eval(site(1L), Clock.systemUTC());
        assertEquals(1, result.size());
        assertEquals("/api/a", result.iterator().next().bucket());
    }

    @Test
    void statusCodeMatchWithEqualsNotEq() {
        // 验证用 .equals 而非 ==：200 和 200 用 == 可能因 Integer 装箱误判
        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(
                rule(10L, "/api/a", 200, 200, 1000L)));
        assertTrue(def.eval(site(1L), Clock.systemUTC()).isEmpty());
    }

    @Test
    void evalSkipsRuleWhenCounterBelowThreshold() {
        var ruleRepo = mock(SitePathRuleRepository.class);
        var configService = mock(ConfigService.class);
        when(configService.getOrDefault(any(), any(ConsecutiveFailureConfig.class)))
                .thenReturn(ConsecutiveFailureConfig.builder()
                        .consecutiveFailuresBeforeAlert(3).build());

        var rule = new SitePathRule();
        rule.setSiteId(1L);
        rule.setPath("/api/orders");
        rule.setExpectedHttpStatus(200);
        rule.setLastHttpStatus(500);  // failing
        rule.setConsecutiveFailures(2);  // counter < threshold

        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(rule));

        var def = new PathCheckAlertDefinition(ruleRepo, configService);
        var site = new Site();
        site.setId(1L);

        var results = def.eval(site, Clock.systemUTC());
        assertTrue(results.isEmpty());
    }

    @Test
    void evalReturnsAbnormalWhenCounterMeetsThreshold() {
        var ruleRepo = mock(SitePathRuleRepository.class);
        var configService = mock(ConfigService.class);
        when(configService.getOrDefault(any(), any(ConsecutiveFailureConfig.class)))
                .thenReturn(ConsecutiveFailureConfig.builder()
                        .consecutiveFailuresBeforeAlert(3).build());

        var rule = new SitePathRule();
        rule.setSiteId(1L);
        rule.setPath("/api/orders");
        rule.setExpectedHttpStatus(200);
        rule.setLastHttpStatus(500);  // failing
        rule.setConsecutiveFailures(3);  // counter == threshold

        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(rule));

        var def = new PathCheckAlertDefinition(ruleRepo, configService);
        var site = new Site();
        site.setId(1L);

        var results = def.eval(site, Clock.systemUTC());
        assertEquals(1, results.size());
        var r = results.iterator().next();
        assertEquals("/api/orders", r.bucket());
        assertEquals(AlertStatus.ABNORMAL, r.status());
    }

    @Test
    void evalReturnsEmptyWhenRulePasses() {
        var ruleRepo = mock(SitePathRuleRepository.class);
        var configService = mock(ConfigService.class);
        when(configService.getOrDefault(any(), any(ConsecutiveFailureConfig.class)))
                .thenReturn(ConsecutiveFailureConfig.builder()
                        .consecutiveFailuresBeforeAlert(3).build());

        var rule = new SitePathRule();
        rule.setSiteId(1L);
        rule.setPath("/api/orders");
        rule.setExpectedHttpStatus(200);
        rule.setLastHttpStatus(200);  // OK
        rule.setConsecutiveFailures(0);

        when(ruleRepo.findBySiteIdOrderByIdAsc(1L)).thenReturn(List.of(rule));

        var def = new PathCheckAlertDefinition(ruleRepo, configService);
        var site = new Site();
        site.setId(1L);

        var results = def.eval(site, Clock.systemUTC());
        assertTrue(results.isEmpty());
    }
}
