package com.siteguard.monitor.service.impl;

import com.siteguard.monitor.dashboard.DashboardAlertAggregationService;
import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.DashboardSummaryDTO;
import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SiteCheckHistory;
import com.siteguard.monitor.probe.PathCheckProbe;
import com.siteguard.monitor.probe.ProbeResult;
import com.siteguard.monitor.probe.SiteProbe;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteCheckServiceImplTest {

    @Mock
    SiteProbe probe;

    @Mock
    SiteCheckHistoryRepository historyRepo;

    @Mock
    SiteRepository siteRepo;

    @Mock
    DashboardAlertAggregationService aggregationService;

    @Mock
    PathCheckProbe pathCheckProbe;

    /// 固定时钟:2026-07-20 12:00 UTC(周一)。运维时段判定基于此时刻。
    /// 使用固定时钟而非系统时间,避免测试因实际执行时间不同而产生 flaky。
    private final Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1_784_000_000_000L), ZoneOffset.UTC);

    private SiteCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        // 手动构造以注入 Clock(与 MonitorTimeConfig.clock() 同类型);避免 @InjectMocks 注入复杂度
        service = new SiteCheckServiceImpl(probe, historyRepo, siteRepo, aggregationService, pathCheckProbe, fixedClock);
    }

    private Site newSite() {
        var s = new Site();
        s.setId(1L);
        s.setName("test");
        s.setUrl("https://example.com");
        s.setAvailabilityStatus(SiteStatus.UNKNOWN);
        return s;
    }

    @Test
    void checkOne_up_writesHistoryAndUpdatesSite() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(ProbeResult.up(200, 123));

        service.checkOne(site);

        var captor = ArgumentCaptor.forClass(SiteCheckHistory.class);
        verify(historyRepo).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(1L, saved.getSiteId());
        assertEquals(CheckStatus.UP, saved.getStatus());
        assertEquals(200, saved.getHttpStatus());
        assertEquals(123, saved.getResponseMs());
        assertEquals(SiteStatus.UP, site.getAvailabilityStatus());
        assertEquals(saved.getCheckedAt(), site.getLastCheckedAt());
        // counter 维护在 snapshot save 后再独立 save 1 次 → UP 归零，必 2 次 save
        verify(siteRepo, times(2)).save(site);
        // UP 探测后 counter 应为 0
        assertEquals(0, site.getConsecutiveAvailabilityFailures());
        verify(pathCheckProbe).probe(site);
    }

    @Test
    void checkOne_timeout_normalizedToDown() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(ProbeResult.timeout());

        service.checkOne(site);

        var captor = ArgumentCaptor.forClass(SiteCheckHistory.class);
        verify(historyRepo).save(captor.capture());
        assertEquals(CheckStatus.TIMEOUT, captor.getValue().getStatus());
        assertEquals(SiteStatus.DOWN, site.getAvailabilityStatus());
        verify(pathCheckProbe).probe(site);
    }

    @Test
    void checkOne_error_normalizedToDown() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(ProbeResult.error("Connection refused"));

        service.checkOne(site);

        assertEquals(SiteStatus.DOWN, site.getAvailabilityStatus());
        verify(pathCheckProbe).probe(site);
    }

    @Test
    void checkOne_withCertInfo_writesCertToSite() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(ProbeResult.up(200, 50, 1735689600000L, "Test CA"));

        service.checkOne(site);

        assertEquals(1735689600000L, site.getCertificateExpiresAt());
        assertEquals("Test CA", site.getCertificateIssuer());
        verify(pathCheckProbe).probe(site);
    }

    @Test
    void checkOne_withNullCert_doesNotOverwriteCertOnSite() {
        var site = newSite();
        // 站点之前已有证书信息
        site.setCertificateExpiresAt(1735689600000L);
        site.setCertificateIssuer("Old CA");
        when(probe.probe(site)).thenReturn(ProbeResult.up(200, 50));

        service.checkOne(site);

        // 探测没拿到证书，site 字段保持原样（不清空，避免偶发抓不到时把已有数据擦掉）
        assertEquals(1735689600000L, site.getCertificateExpiresAt());
        assertEquals("Old CA", site.getCertificateIssuer());
        verify(pathCheckProbe).probe(site);
    }

    /// probe 返回 UP 时，consecutiveAvailabilityFailures 必须归零（与 probe 之前累计多少无关）。
    @Test
    void checkOneResetsAvailabilityCounterOnUpProbe() {
        var site = newSite();
        // 站点之前累计 5 次失败，状态已 DOWN
        site.setAvailabilityStatus(SiteStatus.DOWN);
        site.setConsecutiveAvailabilityFailures(5);
        when(probe.probe(site)).thenReturn(ProbeResult.up(200, 100));

        service.checkOne(site);

        // counter 应归零；UP 时第 2 次 save（独立存 counter）必带新值 0
        verify(siteRepo, atLeastOnce()).save(argThat(s ->
                s.getConsecutiveAvailabilityFailures() == 0));
    }

    /// probe 返回 DOWN 时，counter 必须 +1（连续失败累加）。
    @Test
    void checkOneIncrementsAvailabilityCounterOnDownProbe() {
        var site = newSite();
        site.setAvailabilityStatus(SiteStatus.UP);
        site.setConsecutiveAvailabilityFailures(2);
        when(probe.probe(site)).thenReturn(ProbeResult.error("timeout"));

        service.checkOne(site);

        // counter 应为 3（2 + 1）
        verify(siteRepo, atLeastOnce()).save(argThat(s ->
                s.getConsecutiveAvailabilityFailures() == 3));
    }

    /// counter 维护必须在 snapshot save 之后独立 save：UP/DOWN 各 1 次 siteRepo.save，
    /// 本用例 DOWN 路径至少要触发 2 次 save（snapshot 1 次 + counter 1 次）。
    @Test
    void checkOneCounterUpdateAfterSnapshotSave() {
        var site = newSite();
        site.setAvailabilityStatus(SiteStatus.UP);
        site.setConsecutiveAvailabilityFailures(0);
        when(probe.probe(site)).thenReturn(ProbeResult.error("err"));

        service.checkOne(site);

        // 期望至少 2 次 save：第一次存 snapshot，第二次存 counter
        verify(siteRepo, atLeast(2)).save(any(Site.class));
    }

    @Test
    void checkOne_probeThrows_doesNotPropagate() {
        var site = newSite();
        when(probe.probe(site)).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> service.checkOne(site));

        verify(historyRepo, never()).save(any());
        verify(siteRepo, never()).save(any());
        verify(pathCheckProbe, never()).probe(any(Site.class));
    }

    @Test
    void checkOne_historySaveFails_doesNotPropagate() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(ProbeResult.up(200, 50));
        doThrow(new RuntimeException("db down")).when(historyRepo).save(any());

        assertDoesNotThrow(() -> service.checkOne(site));

        verify(siteRepo, never()).save(any());
        verify(pathCheckProbe, never()).probe(any(Site.class));
    }

    /// checkOne 内层 try/catch 必须吞掉 pathCheckProbe.probe 抛出的 RuntimeException，
    /// 不影响根探测结果、历史落库与 site 快照落库。
    @Test
    void checkOne_pathCheckProbeThrows_doesNotPropagate() {
        var site = newSite();
        when(probe.probe(site)).thenReturn(ProbeResult.up(200, 100));
        var history = new SiteCheckHistory();
        when(historyRepo.save(any(SiteCheckHistory.class))).thenReturn(history);
        doThrow(new RuntimeException("simulated probe failure")).when(pathCheckProbe).probe(site);

        assertDoesNotThrow(() -> service.checkOne(site));

        verify(probe).probe(site);
        verify(historyRepo).save(any(SiteCheckHistory.class));
        // counter 维护独立 save 1 次 → UP 路径共 2 次 save
        verify(siteRepo, times(2)).save(site);
        assertEquals(0, site.getConsecutiveAvailabilityFailures());
        verify(pathCheckProbe).probe(site);
    }

    @Test
    void getDashboard_delegatesToAggregationServiceAndSetsAvg() {
        // aggregationService.aggregate() 自带 summary；本 service 仅补 avg
        var stubSummary = new DashboardSummaryDTO(3, 1, 1, 1, 0, null);
        var stubResponse = new DashboardResponse(stubSummary, List.of());
        when(aggregationService.aggregate()).thenReturn(stubResponse);
        when(historyRepo.avgResponseMsSince(any(Long.class))).thenReturn(150.0);

        var response = service.getDashboard();

        assertEquals(3, response.getSummary().getTotalSites());
        assertEquals(1, response.getSummary().getHealthyCount());
        assertEquals(1, response.getSummary().getAbnormalCount());
        assertEquals(150.0, response.getSummary().getAvgResponseMs());
    }

    @Test
    void getDashboard_handlesNullAvgResponse() {
        var stubSummary = new DashboardSummaryDTO(1, 1, 0, 0, 0, null);
        var stubResponse = new DashboardResponse(stubSummary, List.of());
        when(aggregationService.aggregate()).thenReturn(stubResponse);
        when(historyRepo.avgResponseMsSince(any(Long.class))).thenReturn(null);

        var response = service.getDashboard();

        assertNull(response.getSummary().getAvgResponseMs());
    }

    /// checkAll 必须在分发前过滤掉 paused 站点，避免对暂停站点落库历史/快照。
    @Test
    void checkAll_filtersPausedSitesBeforeDispatch() throws Exception {
        var active = new Site();
        active.setId(1L);
        active.setName("active");
        active.setUrl("https://a.example.com");
        active.setPaused(false);
        var paused = new Site();
        paused.setId(2L);
        paused.setName("paused");
        paused.setUrl("https://b.example.com");
        paused.setPaused(true);
        when(siteRepo.findAll(any(Sort.class))).thenReturn(List.of(active, paused));
        // active 站点必须有合法 probe 结果，否则 save 会因 NPE 被 catch 掉，断言失去意义
        when(probe.probe(active)).thenReturn(ProbeResult.up(200, 50));

        service.checkAll();

        // paused 站点不应被探活：paused 站点不进入 checkOne 调度，所以 historyRepo 仅被 active 写 1 次
        verify(historyRepo, times(1)).save(any(SiteCheckHistory.class));
        // active 站点仍需落库 2 次 siteRepo.save：snapshot 1 次 + counter 1 次
        verify(siteRepo, times(2)).save(any(Site.class));
        // 子路由探测仅对 active 站点调用 1 次，paused 站点不进 checkOne
        verify(pathCheckProbe, times(1)).probe(active);
        verify(pathCheckProbe, never()).probe(paused);
    }

    /// checkAll 必须在分发前过滤掉运维时段内站点:与 paused 同质,按时间表自动跳过。
    /// 固定时钟 2026-07-20 12:00 UTC(周一)并不在任何站点窗口内(maintenance 08:00-22:00 跨越到 22:00 闭),
    /// 这里用跨日窗口 08:00-22:00 把 12:00 圈入窗口内 → 应被过滤。
    @Test
    void checkAll_filtersMaintenanceWindowSites() throws Exception {
        var active = new Site();
        active.setId(1L);
        active.setName("active");
        active.setUrl("https://a.example.com");
        active.setPaused(false);
        // 无运维时段 → 24h 监控,12:00 不受影响
        active.setMaintenance(null);

        var inMaintenance = new Site();
        inMaintenance.setId(2L);
        inMaintenance.setName("maint");
        inMaintenance.setUrl("https://m.example.com");
        inMaintenance.setPaused(false);
        // 跨日窗口 22:00-08:00 → 12:00 不在窗口内,故意反向。这里改用 08:00-22:00 把 12:00 圈入
        inMaintenance.setMaintenance("{\"start\":\"08:00\",\"end\":\"22:00\"}");

        when(siteRepo.findAll(any(Sort.class))).thenReturn(List.of(active, inMaintenance));
        when(probe.probe(active)).thenReturn(ProbeResult.up(200, 50));

        service.checkAll();

        // 运维时段内站点不应被探活:仅 active 写历史 1 次
        verify(historyRepo, times(1)).save(any(SiteCheckHistory.class));
        // 仅 active 站点落库:snapshot + counter 共 2 次
        verify(siteRepo, times(2)).save(any(Site.class));
        // 子路由探测仅对 active 站点调用 1 次
        verify(pathCheckProbe, times(1)).probe(active);
        verify(pathCheckProbe, never()).probe(inMaintenance);
    }

    /// checkOne 对直接传入的 paused 站点必须直接返回，绝不写历史/更新快照。
    @Test
    void checkOne_pausedSite_skipsAndDoesNotWriteHistory() {
        var paused = new Site();
        paused.setId(2L);
        paused.setName("paused");
        paused.setUrl("https://b.example.com");
        paused.setPaused(true);

        service.checkOne(paused);

        verify(historyRepo, never()).save(any(SiteCheckHistory.class));
        verify(siteRepo, never()).save(any(Site.class));
        verify(pathCheckProbe, never()).probe(any(Site.class));
    }

    /// getDashboard 的 pausedCount 由 aggregationService 内部按 SiteHealthClassifier 算出，
    /// 本 service 只补 avg——验证 getDashboard 把 avg 写入 summary 后透传给调用方。
    @Test
    void getDashboard_passesThroughSummaryFromAggregationService() {
        var stubSummary = new DashboardSummaryDTO(3, 1, 1, 0, 1, null);
        var stubResponse = new DashboardResponse(stubSummary, List.of());
        when(aggregationService.aggregate()).thenReturn(stubResponse);
        when(historyRepo.avgResponseMsSince(anyLong())).thenReturn(null);

        var response = service.getDashboard();

        assertEquals(3L, response.getSummary().getTotalSites());
        assertEquals(1L, response.getSummary().getHealthyCount());
        assertEquals(1L, response.getSummary().getAbnormalCount());
        assertEquals(1L, response.getSummary().getPausedCount());
    }

}