package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SiteCheckHistory;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// 可用性告警源：复用 history 表的 (site,status) dedup 查询，
/// 在内存中再按 site 聚合挑最新一条，并按 site 快照剔除已恢复站点，输出 kind=AVAILABILITY。
///
/// repo 用派生表 MAX+INNER JOIN 对 (site,status) 做第一层 dedup；这里二次按 site 聚合保证
/// "一个站点一条 AVAILABILITY"。
///
/// 列表语义 = 当前存在的异常（非发生过的异常）：site 快照 availabilityStatus=UP
/// 表示最近一次探测已恢复，旧 history 行即使存在也不再列入告警。
@Component
@RequiredArgsConstructor
public class AvailabilityAlertSource implements DashboardAlertSource {

    /// 从 repo 拉多少条历史。覆盖 dedup-by-(site,status) 后尚需 site 维度二次聚合的情况；
    /// 上限取决于 site 数 × status 数，远多于最终聚合 cap (20)。
    ///
    /// TODO: 站点数增长到一定量级时需重新评估；当前 100 足以覆盖 ~30 站点 × 3 种非 UP 状态。
    private static final int HISTORY_FETCH_LIMIT = 100;

    private final SiteCheckHistoryRepository historyRepo;

    @Override
    public AlertKind kind() {
        return AlertKind.AVAILABILITY;
    }

    @Override
    public List<AlertDTO> load(List<Site> allSites) {
        var sitesById = allSites.stream().collect(Collectors.toMap(Site::getId, s -> s));

        // repo 用派生表 MAX+INNER JOIN 对 (site,status) 做了第一层 dedup
        List<SiteCheckHistory> history = historyRepo.findRecentIssues(
                PageRequest.of(0, HISTORY_FETCH_LIMIT));

        // 第二层：按 site 聚合挑 checkedAt 最新；物理删除残留场景用 Optional 滤掉
        return history.stream()
                .collect(Collectors.groupingBy(
                        SiteCheckHistory::getSiteId,
                        Collectors.maxBy(Comparator.comparingLong(SiteCheckHistory::getCheckedAt))))
                .values().stream()
                .flatMap(Optional::stream)
                // 列表语义 = 当前存在的异常，而非发生过的异常：
                // site 快照 availabilityStatus=UP 表示最近一次探测已恢复，
                // 旧 history 行（无论何种 status）不再列入告警。
                // 用户暂停的站点也排除：扫描层 (T9) 已停止写入新 history，
                // 但旧行可能仍存在，告警面板不应再展示。
                // 物理删除残留的 site=null 由下游 toAlertIfSiteExists 兜底。
                .filter(h -> {
                    var site = sitesById.get(h.getSiteId());
                    return site != null
                            && !site.isPaused()
                            && site.getAvailabilityStatus() != SiteStatus.UP;
                })
                .map(h -> toAlertIfSiteExists(h, sitesById))
                .flatMap(Optional::stream)
                .toList();
    }

    /// history 表可能残留已删除站点的行（物理删除或外键未级联）；
    /// 若无对应 site 则返回 empty，避免产生幽灵告警。
    private Optional<AlertDTO> toAlertIfSiteExists(SiteCheckHistory h, Map<Long, Site> sitesById) {
        var site = sitesById.get(h.getSiteId());
        if (site == null) {
            return Optional.empty();
        }
        // 仪表盘只展示"当前存在异常"的站点；上游 filter 已剔除 availabilityStatus=UP，
        // 落入此处的历史行 status 必为 DOWN / TIMEOUT / ERROR，统一记 ABNORMAL。
        var status = AlertStatus.ABNORMAL;
        return Optional.of(new AlertDTO(
                site.getId(),
                site.getName(),
                site.getUrl(),
                AlertKind.AVAILABILITY,
                status,
                h.getCheckedAt(),
                buildMessage(h)));
    }

    private String buildMessage(SiteCheckHistory h) {
        return switch (h.getStatus()) {
            case DOWN -> {
                if (h.getHttpStatus() != null) {
                    yield "HTTP " + h.getHttpStatus()
                            + (h.getErrorMessage() != null ? ": " + h.getErrorMessage() : "");
                }
                yield h.getErrorMessage() != null ? h.getErrorMessage() : "服务不可用";
            }
            case TIMEOUT -> "请求超时 (" + (h.getResponseMs() != null ? h.getResponseMs() : "?") + "ms)";
            case ERROR -> h.getErrorMessage() != null ? "Error: " + h.getErrorMessage() : "连接失败";
            // AVAILABILITY 源不应出现 UP；保留作为类型完整性兜底
            case UP -> h.getErrorMessage() != null ? h.getErrorMessage() : "OK";
        };
    }
}
