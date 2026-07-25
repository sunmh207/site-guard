package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.entity.CheckStatus;
import com.siteguard.monitor.entity.SiteCheckHistory;
import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import com.siteguard.site.entity.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// 可用性告警源：从 site_check_state 拉取已经达到连续失败阈值的 AVAILABILITY 状态。
///
/// site_check_state 是告警状态机的展示真相：只有 AlertDetectionService 写入
/// AVAILABILITY/DOWN 后，主站才进入 Dashboard 异常列表。history 只用于补充最近一次
/// 探测的 HTTP/超时/错误详情，不参与异常是否存在的判断。
@Component
@RequiredArgsConstructor
public class AvailabilityAlertSource implements DashboardAlertSource {

    /// history 只用于补充文案，查询数量不影响 state 驱动的告警展示语义。
    private static final int HISTORY_FETCH_LIMIT = 100;

    private final SiteCheckStateRepository stateRepo;
    private final SiteCheckHistoryRepository historyRepo;

    @Override
    public AlertKind kind() {
        return AlertKind.AVAILABILITY;
    }

    @Override
    public List<AlertDTO> load(List<Site> allSites) {
        Map<Long, Site> sitesById = allSites.stream()
                .collect(Collectors.toMap(Site::getId, Function.identity()));
        Map<Long, SiteCheckHistory> latestHistoryBySite = latestHistoryBySite();

        return stateRepo.findByAlertKind(AlertKind.AVAILABILITY).stream()
                .filter(state -> "DOWN".equals(state.getId().bucket()))
                .map(state -> toAlert(state, sitesById, latestHistoryBySite))
                .flatMap(Optional::stream)
                .toList();
    }

    private Map<Long, SiteCheckHistory> latestHistoryBySite() {
        List<SiteCheckHistory> history = historyRepo.findRecentIssues(
                PageRequest.of(0, HISTORY_FETCH_LIMIT));

        // history 只提供告警详情；同一站点取最近一条，告警时间仍使用 state.updatedAt。
        return history.stream()
                .collect(Collectors.toMap(
                        SiteCheckHistory::getSiteId,
                        Function.identity(),
                        (left, right) -> left.getCheckedAt() >= right.getCheckedAt() ? left : right));
    }

    /// 状态行可能指向已删除/暂停的站点；这些情况直接过滤。
    private Optional<AlertDTO> toAlert(SiteCheckState state,
                                       Map<Long, Site> sitesById,
                                       Map<Long, SiteCheckHistory> latestHistoryBySite) {
        Site site = sitesById.get(state.getId().siteId());
        if (site == null || site.isPaused()) {
            return Optional.empty();
        }

        SiteCheckHistory history = latestHistoryBySite.get(site.getId());
        String message = history == null ? "当前不可用" : buildMessage(history);
        return Optional.of(new AlertDTO(
                site.getId(),
                site.getName(),
                site.getUrl(),
                AlertKind.AVAILABILITY,
                AlertStatus.ABNORMAL,
                state.getUpdatedAt(),
                message));
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
            // AVAILABILITY 源只读取 DOWN state；保留作为类型完整性兜底。
            case UP -> h.getErrorMessage() != null ? h.getErrorMessage() : "OK";
        };
    }
}
