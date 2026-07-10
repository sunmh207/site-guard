package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/// 子路由检测告警源：从 site_check_state 拉所有 (kind=PATH_CHECK) 行，
/// 每行生成一条 AlertDTO（一个 failing path 一条）。
///
/// 与 AvailabilityAlertSource 不同：本源不写 site_check_history（设计 §5.1 决策），
/// 所以直接从状态机表读"当前边沿异常的路径集合"。
///
/// site_path_rule 用于补充每条 failing path 的可读消息（实际状态码 / 错误摘要）；
/// 规则已被删除的路径走 fallback 文案。
///
/// DashboardAlertAggregationService 已按 DashboardAlertSource 列表聚合，
/// 本 bean 由 Spring 自动注入并被聚合（无需修改聚合服务）。
@Component
@RequiredArgsConstructor
public class PathCheckAlertSource implements DashboardAlertSource {

    private final SiteCheckStateRepository stateRepo;
    private final SitePathRuleRepository ruleRepo;

    @Override
    public AlertKind kind() {
        return AlertKind.PATH_CHECK;
    }

    @Override
    public List<AlertDTO> load(List<Site> allSites) {
        Map<Long, Site> sitesById = allSites.stream()
                .collect(Collectors.toMap(Site::getId, s -> s));

        // 一次性按 siteId 拉所有规则，建索引避免 N+1
        Map<Long, Map<String, SitePathRule>> rulesBySitePath = new HashMap<>();
        for (Site s : allSites) {
            Map<String, SitePathRule> map = ruleRepo.findBySiteIdOrderByIdAsc(s.getId())
                    .stream()
                    .collect(Collectors.toMap(SitePathRule::getPath, r -> r, (a, b) -> a));
            rulesBySitePath.put(s.getId(), map);
        }

        return stateRepo.findByAlertKind(AlertKind.PATH_CHECK).stream()
                .map(state -> toAlert(state, sitesById, rulesBySitePath))
                .flatMap(Optional::stream)
                .toList();
    }

    /// 状态行可能指向已删除/暂停的站点；这些情况直接过滤。
    private Optional<AlertDTO> toAlert(SiteCheckState state,
                                       Map<Long, Site> sitesById,
                                       Map<Long, Map<String, SitePathRule>> rulesBySitePath) {
        Site site = sitesById.get(state.getId().siteId());
        if (site == null || site.isPaused()) {
            return Optional.empty();
        }

        String pathKey = state.getId().bucket();   // PATH_CHECK 时 bucket = pathKey
        SitePathRule rule = rulesBySitePath.getOrDefault(site.getId(), Map.of()).get(pathKey);
        String message = buildMessage(pathKey, rule);

        return Optional.of(new AlertDTO(
                site.getId(),
                site.getName(),
                site.getUrl(),
                AlertKind.PATH_CHECK,
                AlertStatus.ABNORMAL,
                state.getUpdatedAt(),
                message));
    }

    private String buildMessage(String pathKey, SitePathRule rule) {
        if (rule == null) {
            return "路径 " + pathKey + " 检测异常（规则已删除）";
        }
        Integer got = rule.getLastHttpStatus();
        Integer expected = rule.getExpectedHttpStatus();
        if (got == null) {
            String err = rule.getLastErrorMessage() == null ? "未探测" : rule.getLastErrorMessage();
            return String.format("路径 %s 探测失败（%s），期望 %d", pathKey, err, expected);
        }
        return String.format("路径 %s 返回 %d，期望 %d", pathKey, got, expected);
    }
}