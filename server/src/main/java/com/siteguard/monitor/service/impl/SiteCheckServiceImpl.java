package com.siteguard.monitor.service.impl;

import com.siteguard.monitor.dashboard.DashboardAlertAggregationService;
import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.SiteCheckHistoryDTO;
import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SiteCheckHistory;
import com.siteguard.monitor.probe.PathCheckProbe;
import com.siteguard.monitor.probe.SiteProbe;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.monitor.service.SiteCheckService;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteMaintenance;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/// 监控服务实现。
///
/// - checkAll: 拉全量站点 → 虚拟线程并发探测 → 写历史 + 更新快照
/// - checkOne: 单站点的探测 + 落库；任何步骤异常都被捕获并 log，绝不向上抛
/// - getDashboard: 仪表盘聚合委托给 aggregation service（桶分 + alerts）；本方法只补 avg
///
/// 关键不变量：单个站点失败不影响其他站点；Service 层不抛任何业务异常。
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteCheckServiceImpl implements SiteCheckService {

    /// 仪表盘查询的"近 1 小时"窗口，单位毫秒
    private static final long ONE_HOUR_MS = 3_600_000L;

    /// 探测历史 slideover 的硬上限：listRecent 不论外部传多少，最多返回 MAX_RECENT_HISTORY 条。
    /// 前端默认 30，这里钳到 30 与产品要求一致；防御性兜底，避免手工绕过造成大查询。
    private static final int MAX_RECENT_HISTORY = 30;

    private final SiteProbe siteProbe;
    private final SiteCheckHistoryRepository historyRepo;
    private final SiteRepository siteRepo;
    private final DashboardAlertAggregationService aggregationService;
    /// 子路由探测：根探测完成后调用，遍历站点的 path rule 做 HTTP GET 并回写规则行
    private final PathCheckProbe pathCheckProbe;
    /// 站点运维时段判定使用:取"此刻"时间,结合 Site.maintenance JSON 按时间表自动跳过探测/告警。
    /// 注入 MonitorTimeConfig.clock() 而不是 LocalDateTime.now(),便于测试伪造时间点。
    private final Clock clock;

    @Override
    public void checkAll() {
        // 整轮扫描共用一个 now,避免跨整点时有的站点判在运维内、有的判在外(同轮判定一致)。
        // 取 Instant 传入 SiteMaintenance,由其按 SiteMaintenance.DEFAULT_ZONE(Asia/Shanghai) 解读 wall-clock 时间。
        var now = Instant.now(clock);
        var sites = siteRepo.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                // 分发前过滤掉 paused 站点：暂停站点不参与扫描、不写历史/快照
                .filter(s -> !s.isPaused())
                // 运维时段内站点同样跳过(与 paused 同质,按时间表自动开关)
                .filter(s -> !SiteMaintenance.isInMaintenance(s, now))
                .toList();
        if (sites.isEmpty()) {
            return;
        }
        // try-with-resources 保证 executor 被关闭；Java 25 虚拟线程
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = sites.stream()
                    .map(site -> CompletableFuture.runAsync(() -> checkOne(site), executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        }
        log.debug("Site check completed for {} sites", sites.size());
    }

    @Override
    public void checkOne(Site site) {
        // 二次防护：findAll 快照与 dispatch 之间被 setPaused(true) 改状态的站点，单次扫描丢弃
        if (site.isPaused()) {
            log.debug("Skip paused site id={}", site.getId());
            return;
        }
        long checkedAt = System.currentTimeMillis();
        try {
            var result = siteProbe.probe(site);
            // 写历史：失败只 log，不阻塞 site 状态更新
            try {
                var history = new SiteCheckHistory();
                history.setSiteId(site.getId());
                history.setCheckedAt(checkedAt);
                history.setStatus(result.status());
                history.setHttpStatus(result.httpStatus());
                history.setResponseMs(result.responseMs());
                history.setErrorMessage(result.errorMessage());
                historyRepo.save(history);
            } catch (RuntimeException e) {
                log.warn("Failed to save site check history for site {}: {}", site.getId(), e.getMessage());
                return;
            }
            // 更新 site 快照：失败只 log，不抛出
            site.setAvailabilityStatus(toSnapshotStatus(result.status()));
            site.setLastCheckedAt(checkedAt);
            // 证书快照：仅当 probe 真的拿到证书时才覆盖；否则保留 site 已有值
            // （避免一次抓不到时把已有数据擦成 null）
            if (result.certExpiresAt() != null) {
                site.setCertificateExpiresAt(result.certExpiresAt());
            }
            if (result.certIssuer() != null) {
                site.setCertificateIssuer(result.certIssuer());
            }
            try {
                siteRepo.save(site);
            } catch (RuntimeException e) {
                log.warn("Failed to update site snapshot for site {}: {}", site.getId(), e.getMessage());
            }
            /// 连续失败 counter 维护（独立第 2 次 save）：
            /// - probe 返 UP → counter 归零（站点恢复，失败累计清零）
            /// - probe 返 DOWN/TIMEOUT/ERROR → counter +1（连续失败累加）
            /// 必须放在 snapshot save 之后再算再 save：保证 snapshot 落库失败时 counter 不会
            /// 被错误地 in-memory 更新（与磁盘状态解耦）；本次 save 自身失败也仅 log，不阻塞 path 探测。
            int currentCounter = site.getConsecutiveAvailabilityFailures();
            int newCounter = (toSnapshotStatus(result.status()) == SiteStatus.UP)
                    ? 0
                    : currentCounter + 1;
            site.setConsecutiveAvailabilityFailures(newCounter);
            try {
                siteRepo.save(site);
            } catch (RuntimeException e) {
                log.warn("Failed to update availability failure counter for site {}: {}",
                        site.getId(), e.getMessage());
            }
            // 子路由探测：根探测 + 快照落库成功后执行；probe 内部已吞掉单条规则异常，这里再包一层兜底
            try {
                pathCheckProbe.probe(site);
            } catch (RuntimeException e) {
                log.warn("Path rule probe failed for site {}: {}", site.getId(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("Unexpected failure while checking site {}: {}", site.getId(), e.getMessage());
        }
    }

    @Override
    public DashboardResponse getDashboard() {
        // 摘要分桶由 aggregationService 内部基于同一份 sites + alerts 算出，
        // 本方法只补 avgResponseMs（依赖独立时间窗口查询，与分桶无关）。
        var response = aggregationService.aggregate();
        long oneHourAgo = System.currentTimeMillis() - ONE_HOUR_MS;
        response.getSummary().setAvgResponseMs(historyRepo.avgResponseMsSince(oneHourAgo));
        return response;
    }

    /// 把探测的 4 态映射到 site 表快照的 3 态：
    /// - UP → UP
    /// - DOWN / TIMEOUT / ERROR → DOWN
    private SiteStatus toSnapshotStatus(CheckStatus status) {
        return status == CheckStatus.UP ? SiteStatus.UP : SiteStatus.DOWN;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteCheckHistoryDTO> listRecent(long siteId, int limit) {
        /// 钳制 limit：下限 1、上限 MAX_RECENT_HISTORY。外部传 0/负数/超大值都收敛到合法范围。
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT_HISTORY));
        var pageable = PageRequest.of(0, safeLimit);
        return historyRepo.findBySiteIdOrderByCheckedAtDesc(siteId, pageable).stream()
                .map(SiteCheckServiceImpl::toDto)
                .toList();
    }

    /// entity → DTO 映射。当前两表字段完全 1:1，未来 DTO 加展示字段（如耗时格式化）只在这里改。
    private static SiteCheckHistoryDTO toDto(SiteCheckHistory h) {
        var dto = new SiteCheckHistoryDTO();
        dto.setId(h.getId());
        dto.setSiteId(h.getSiteId());
        dto.setCheckedAt(h.getCheckedAt());
        dto.setStatus(h.getStatus());
        dto.setHttpStatus(h.getHttpStatus());
        dto.setResponseMs(h.getResponseMs());
        dto.setErrorMessage(h.getErrorMessage());
        return dto;
    }
}
