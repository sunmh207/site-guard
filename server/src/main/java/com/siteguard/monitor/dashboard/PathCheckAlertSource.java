package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.alert.detection.SiteCheckState;
import com.siteguard.monitor.alert.detection.SiteCheckStateRepository;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.probe.PathCheckType;
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
        // KEYWORD 模式：消息聚焦于期望关键字与命中情况，HTTP 状态码已无意义；
        // 与 PathCheckAlertDefinition.formatFailure 保持一致的文案风格，
        // 避免 IM 通知与 dashboard 两套消息互相矛盾。
        if (rule.getCheckType() == PathCheckType.KEYWORD) {
            if (rule.getLastTextMatched() == null) {
                // 探测本身失败/超时，body 尚未拿到，展示错误原因 + 期望关键字
                String err = rule.getLastErrorMessage() == null ? "结果缺失" : rule.getLastErrorMessage();
                return String.format("路径 %s 探测失败（%s），期望包含「%s」",
                        pathKey, err, rule.getExpectedText());
            }
            if (rule.getLastTextMatched()) {
                // body 命中关键字：isFailing 不会放 true 进来，但方法自身逻辑闭环——
                // 命中即正常，不应出现在告警消息里，降级走通用文案。
                return String.format("路径 %s 检测异常", pathKey);
            }
            // body 已拿到但未命中关键字
            return String.format("路径 %s 未包含期望文本「%s」", pathKey, rule.getExpectedText());
        }
        // HTTP_STATUS：以下现有逻辑不变
        Integer got = rule.getLastHttpStatus();
        Integer expected = rule.getExpectedHttpStatus();
        if (got == null) {
            String err = rule.getLastErrorMessage() == null ? "未探测" : rule.getLastErrorMessage();
            return String.format("路径 %s 探测失败（%s），期望 %d", pathKey, err, expected);
        }
        return String.format("路径 %s 返回 %d，期望 %d", pathKey, got, expected);
    }
}