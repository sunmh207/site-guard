package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition;
import com.siteguard.monitor.alert.AlertDefinition.EvalResult;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.notification.NotificationEvent;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.repository.SiteRepository;
import com.siteguard.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDetectionServiceTest {

    @Mock private SiteRepository siteRepo;
    @Mock private SiteCheckStateRepository stateRepo;
    @Mock private SitePathRuleRepository pathRuleRepo;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ConfigService configService;

    private final Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    private AlertDetectionService svc;

    private static Site site(long id) {
        var s = new Site();
        s.setId(id);
        s.setName("site-" + id);
        s.setUrl("https://s" + id + ".example");
        return s;
    }

    private static SiteCheckState stateRow(long siteId, String kind, String bucket) {
        return SiteCheckState.builder()
                .id(new SiteCheckStateId(siteId, kind, bucket))
                .lastNotifiedAt(0L)
                .updatedAt(0L)
                .build();
    }

    /// 构造带 expectedHttpStatus 的 rule stub；PATH_CHECK 恢复消息拼装依赖此字段
    private static SitePathRule ruleWithExpected(long siteId, String path, int expected) {
        var r = new SitePathRule();
        r.setSiteId(siteId);
        r.setPath(path);
        r.setExpectedHttpStatus(expected);
        return r;
    }

    @BeforeEach
    void setUp() {
        var def = new PathCheckAlertDefinition(pathRuleRepo, configService);  // 仅做编译签名占位，真实测试用 StubDefinition
        svc = new AlertDetectionService(siteRepo, stateRepo, pathRuleRepo, List.of(def), publisher, fixedClock);
    }

    /// 注入自定义 EvalResult 集合的简单 Stub
    private static class StubDefinition implements AlertDefinition {
        private final AlertKind kind;
        private final Map<Long, Set<EvalResult>> bySite;
        StubDefinition(AlertKind kind, Map<Long, Set<EvalResult>> bySite) {
            this.kind = kind; this.bySite = bySite;
        }
        @Override public AlertKind kind() { return kind; }
        @Override public Set<EvalResult> eval(Site site, Clock clock) {
            return bySite.getOrDefault(site.getId(), Set.of());
        }
    }

    private AlertDetectionService svcWith(AlertDefinition def) {
        return new AlertDetectionService(siteRepo, stateRepo, pathRuleRepo, List.of(def), publisher, fixedClock);
    }

    @Test
    void pausedSite_skipped() {
        var s = site(1L);
        s.setPaused(true);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        svc.detectAll();
        verifyNoInteractions(publisher);
    }

    @Test
    void pathCheck_firstTimeAllOk_noEventNoStateWrite() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of());
        var def = new StubDefinition(AlertKind.PATH_CHECK, Map.of(1L, Set.of()));
        svcWith(def).detectAll();
        verifyNoInteractions(publisher);
        verify(stateRepo, never()).save(any());
    }

    @Test
    void pathCheck_firstTimeFailure_publishesAbnormalAndInsertsState() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of());
        var def = new StubDefinition(AlertKind.PATH_CHECK, Map.of(
                1L, Set.of(new EvalResult("/api/orders", AlertStatus.ABNORMAL, "路径 /api/orders 返回 500，期望 200"))));
        svcWith(def).detectAll();

        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        NotificationEvent ev = captor.getValue();
        assertEquals(AlertKind.PATH_CHECK, ev.alertKind());
        assertEquals(AlertStatus.ABNORMAL, ev.status());
        assertEquals("/api/orders", ev.bucket());
        assertTrue(ev.message().contains("/api/orders"));

        verify(stateRepo).save(any(SiteCheckState.class));
    }

    @Test
    void pathCheck_recovery_publishesNormalAndDeletesState() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        var oldRow = stateRow(1L, "PATH_CHECK", "/api/orders");
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(oldRow));
        /// 恢复时反查 rule 取 expected_http_status 拼到消息末尾
        when(pathRuleRepo.findBySiteIdAndPath(1L, "/api/orders"))
                .thenReturn(Optional.of(ruleWithExpected(1L, "/api/orders", 200)));
        var def = new StubDefinition(AlertKind.PATH_CHECK, Map.of(1L, Set.of()));   // all ok
        svcWith(def).detectAll();

        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        NotificationEvent ev = captor.getValue();
        assertEquals(AlertStatus.NORMAL, ev.status());
        assertEquals("/api/orders", ev.bucket());
        assertTrue(ev.message().contains("/api/orders"));
        assertTrue(ev.message().contains("已恢复"));
        /// 新格式：恢复消息带"期望 200"；旧格式末尾不再追加"（路径：...）"
        assertTrue(ev.message().contains("期望 200"), "got: " + ev.message());
        assertFalse(ev.message().contains("（路径："), "got: " + ev.message());

        verify(stateRepo).deleteBySiteIdAndAlertKindAndBucketIn(eq(1L), eq(AlertKind.PATH_CHECK), eq(Set.of("/api/orders")));
        verify(stateRepo, never()).save(any());
    }

    /// PATH_CHECK 恢复时若 rule 已被删除（异常期间被删），降级为不带期望码的旧格式，
    /// 不强造状态码，避免误导运维
    @Test
    void pathCheck_recovery_ruleMissing_fallsBackToBareMessage() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        var oldRow = stateRow(1L, "PATH_CHECK", "/api/deleted");
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(oldRow));
        when(pathRuleRepo.findBySiteIdAndPath(1L, "/api/deleted")).thenReturn(Optional.empty());
        var def = new StubDefinition(AlertKind.PATH_CHECK, Map.of(1L, Set.of()));
        svcWith(def).detectAll();

        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        NotificationEvent ev = captor.getValue();
        assertEquals(AlertStatus.NORMAL, ev.status());
        assertTrue(ev.message().contains("子路由 `/api/deleted` 已恢复"), "got: " + ev.message());
        assertFalse(ev.message().contains("期望"), "got: " + ev.message());
        assertFalse(ev.message().contains("（路径："), "got: " + ev.message());
    }

    @Test
    void pathCheck_oneRecoveryOneNewInSameTick_emitsTwoEvents() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of(
                stateRow(1L, "PATH_CHECK", "/api/old"),      // will recover
                stateRow(1L, "PATH_CHECK", "/api/stable")));  // still failing
        /// 恢复的 /api/old 取不到 rule 也不影响本测试断言（只关心事件数量和 bucket），
        /// 走降级路径即可
        lenient().when(pathRuleRepo.findBySiteIdAndPath(1L, "/api/old"))
                .thenReturn(Optional.empty());
        var def = new StubDefinition(AlertKind.PATH_CHECK, Map.of(1L, Set.of(
                new EvalResult("/api/new", AlertStatus.ABNORMAL, "new fail"),
                new EvalResult("/api/stable", AlertStatus.ABNORMAL, "stable fail"))));
        svcWith(def).detectAll();

        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        List<NotificationEvent> events = captor.getAllValues();
        NotificationEvent recovery = events.stream()
                .filter(e -> e.status() == AlertStatus.NORMAL).findFirst().orElseThrow();
        assertEquals("/api/old", recovery.bucket());
        NotificationEvent newFail = events.stream()
                .filter(e -> e.status() == AlertStatus.ABNORMAL).findFirst().orElseThrow();
        assertEquals("/api/new", newFail.bucket());
    }

    @Test
    void certLevelChangeWithW3ToW2_emitsOnlyAbnormalNoRecoveryEvent() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.CERT_EXPIRY)).thenReturn(List.of(
                stateRow(1L, "CERT_EXPIRY", "W7")));
        var def = new StubDefinition(AlertKind.CERT_EXPIRY, Map.of(1L, Set.of(
                new EvalResult("W3", AlertStatus.ABNORMAL, "证书将于 3 天后过期"))));
        svcWith(def).detectAll();

        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());
        NotificationEvent ev = captor.getValue();
        assertEquals(AlertStatus.ABNORMAL, ev.status());
        assertEquals("W3", ev.bucket());
    }

    @Test
    void certRecoveryToOk_emitsNormalEvent() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.CERT_EXPIRY)).thenReturn(List.of(
                stateRow(1L, "CERT_EXPIRY", "W7")));
        var def = new StubDefinition(AlertKind.CERT_EXPIRY, Map.of(1L, Set.of(
                new EvalResult("OK", AlertStatus.NORMAL, "证书有效期已恢复"))));
        svcWith(def).detectAll();

        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        NotificationEvent ev = captor.getValue();
        assertEquals(AlertStatus.NORMAL, ev.status());
        assertEquals("W7", ev.bucket());
        assertEquals("证书有效期已恢复", ev.message());
    }

    @Test
    void firstTimeCertOk_noEventButStateWritten() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.CERT_EXPIRY)).thenReturn(List.of());
        var def = new StubDefinition(AlertKind.CERT_EXPIRY, Map.of(1L, Set.of(
                new EvalResult("OK", AlertStatus.NORMAL, "证书有效期已恢复"))));
        svcWith(def).detectAll();

        verifyNoInteractions(publisher);
        verify(stateRepo).save(any(SiteCheckState.class));
    }

    @Test
    void evalThrowsForOneSite_doesNotAffectNext() {
        var s1 = site(1L);
        var s2 = site(2L);
        when(siteRepo.findAll()).thenReturn(List.of(s1, s2));
        when(stateRepo.findByAlertKind(AlertKind.PATH_CHECK)).thenReturn(List.of());

        AlertDefinition throwing = new AlertDefinition() {
            @Override public AlertKind kind() { return AlertKind.PATH_CHECK; }
            @Override public Set<EvalResult> eval(Site site, Clock clock) {
                if (site.getId() == 1L) throw new RuntimeException("boom");
                return Set.of(new EvalResult("/x", AlertStatus.ABNORMAL, "x"));
            }
        };
        svcWith(throwing).detectAll();

        // s2 仍然发了事件
        var captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());
        assertEquals("/x", captor.getValue().bucket());
    }

    /// 用户报告 bug 复现：site 临时 DOWN 但 counter 未达 threshold，eval 返回空集。
    /// 旧 state `UP` 消失 → 不能误发 NORMAL 通知"可用性已恢复"，
    /// 否则抖动场景会反复触发误报（counter 累积不到 threshold 永远没机会发 ABNORMAL）。
    @Test
    void availability_emptyEvalWithOldUpState_publishesNothing() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                stateRow(1L, "AVAILABILITY", "UP")));
        // counter < threshold 时 AvailabilityAlertDefinition.eval 返回空集
        var def = new StubDefinition(AlertKind.AVAILABILITY, Map.of(1L, Set.of()));
        svcWith(def).detectAll();

        // 关键断言：不能发任何事件，守卫阻断"暂无可发事件"被误读为"已恢复"
        verifyNoInteractions(publisher);
    }

    /// 抖动全流程：
    /// - T1: state UP 存在 + eval={}（counter<threshold）→ 静默（守卫阻断 NORMAL）
    /// - T2: state UP 存在 + eval={UP, NORMAL} → 静默（交集）
    ///   整个过程中不应有任何"误报恢复"通知
    @Test
    void availability_emptyEvalThenUpEval_neverPublishes() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(
                stateRow(1L, "AVAILABILITY", "UP")));

        // T1: counter<threshold → eval={}
        var def1 = new StubDefinition(AlertKind.AVAILABILITY, Map.of(1L, Set.of()));
        svcWith(def1).detectAll();
        verifyNoInteractions(publisher);

        // T2: state UP 存在 + eval={UP, NORMAL}（真正 UP）→ 静默（交集）
        var def2 = new StubDefinition(AlertKind.AVAILABILITY, Map.of(1L,
                Set.of(new EvalResult("UP", AlertStatus.NORMAL, "可用性已恢复"))));
        svcWith(def2).detectAll();
        verifyNoInteractions(publisher);
    }

    /// CERT_EXPIRY 防御性测试：expiresAt=null 时 eval 返回空集，
    /// 旧 ABNORMAL state 不应触发误发 NORMAL。当前 SiteCheckServiceImpl 实现下此场景
    /// 实际不触发（expiresAt 只在 probe 真的拿到证书时才覆盖），但守卫能防御未来重构风险。
    @Test
    void certExpiry_emptyEvalWithOldAbnormalState_publishesNothing() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        when(stateRepo.findByAlertKind(AlertKind.CERT_EXPIRY)).thenReturn(List.of(
                stateRow(1L, "CERT_EXPIRY", "W7")));  // 旧 state W7
        // expiresAt=null → eval={}
        var def = new StubDefinition(AlertKind.CERT_EXPIRY, Map.of(1L, Set.of()));
        svcWith(def).detectAll();

        // 关键断言：守卫对非 PATH_CHECK 的 CERT_EXPIRY 同样生效
        verifyNoInteractions(publisher);
    }

    /// 恢复事件透传 SiteCheckState.lastNotifiedAt 作为 abnormalStartedAt
    @Test
    void recoveryEvent_passesAbnormalStartedAtToPublisher() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        long abnormalStart = 1_699_999_900_000L;
        SiteCheckState oldState = stateRow(1L, "AVAILABILITY", "DOWN");
        oldState.setLastNotifiedAt(abnormalStart);
        oldState.setUpdatedAt(abnormalStart);
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(oldState));
        var def = new StubDefinition(AlertKind.AVAILABILITY, Map.of(
                1L, Set.of(new EvalResult("UP", AlertStatus.NORMAL, "可用性已恢复"))));
        when(stateRepo.findById(new SiteCheckStateId(1L, "AVAILABILITY", "DOWN")))
                .thenReturn(Optional.of(oldState));

        svcWith(def).detectAll();

        var cap = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(cap.capture());
        var normals = cap.getAllValues().stream()
                .filter(e -> e.status() == AlertStatus.NORMAL).toList();
        assertEquals(1, normals.size(), "应有 1 条 NORMAL 事件");
        assertEquals(Long.valueOf(abnormalStart), normals.get(0).abnormalStartedAt());
    }

    /// SiteCheckState 行缺失 → 透传 null，监听器侧会用 "—" 兜底
    @Test
    void recoveryEvent_siteStateMissing_passesNullAbnormalStartedAt() {
        var s = site(1L);
        when(siteRepo.findAll()).thenReturn(List.of(s));
        long abnormalStart = 1_699_999_900_000L;
        SiteCheckState oldState = stateRow(1L, "AVAILABILITY", "DOWN");
        oldState.setLastNotifiedAt(abnormalStart);
        oldState.setUpdatedAt(abnormalStart);
        when(stateRepo.findByAlertKind(AlertKind.AVAILABILITY)).thenReturn(List.of(oldState));
        var def = new StubDefinition(AlertKind.AVAILABILITY, Map.of(
                1L, Set.of(new EvalResult("UP", AlertStatus.NORMAL, "可用性已恢复"))));
        when(stateRepo.findById(new SiteCheckStateId(1L, "AVAILABILITY", "DOWN")))
                .thenReturn(Optional.empty());

        svcWith(def).detectAll();

        var cap = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(cap.capture());
        var normals = cap.getAllValues().stream()
                .filter(e -> e.status() == AlertStatus.NORMAL).toList();
        assertEquals(1, normals.size());
        assertNull(normals.get(0).abnormalStartedAt(),
                "SiteCheckState 缺失时 abnormalStartedAt 应为 null");
    }
}
