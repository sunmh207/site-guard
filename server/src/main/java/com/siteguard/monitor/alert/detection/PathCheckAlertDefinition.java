package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.monitor.entity.SitePathRule;
import com.siteguard.monitor.probe.PathCheckType;
import com.siteguard.monitor.repository.SitePathRuleRepository;
import com.siteguard.site.entity.Site;
import com.siteguard.system.config.ConsecutiveFailureConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 子路由检测告警定义（连续失败阈值版本）。
///
/// 纯函数：只读 site_path_rule 表的 last_* 与 consecutive_failures 字段，不发起任何 HTTP。
///
/// 每次 eval 返回该站点本次 tick 的"真值集合"：每条 failing 且 counter >= threshold 的路径
/// 对应一个 EvalResult，bucket = pathKey（如 "/api/orders"），status = ABNORMAL。
/// 全 OK / counter 未达 / 无规则返回空集。
///
/// 失败判定复用 SitePathRule.isFailing 静态方法（probe 层与 detector 层共用）。
///
/// 阈值由 ConfigService 每次 eval 时实时读取：用户在 /api/v1/admin/config 改完即生效。
/// 配置缺失/字段为 null → 降级到 ConsecutiveFailureConfig.defaultValue() = 1。
@Component
public class PathCheckAlertDefinition implements AlertDefinition {

    private final SitePathRuleRepository ruleRepo;
    private final ConfigService configService;

    public PathCheckAlertDefinition(SitePathRuleRepository ruleRepo,
                                    ConfigService configService) {
        this.ruleRepo = ruleRepo;
        this.configService = configService;
    }

    @Override
    public AlertKind kind() {
        return AlertKind.PATH_CHECK;
    }

    @Override
    public Set<EvalResult> eval(Site site, Clock clock) {
        int threshold = resolveThreshold(site);
        List<SitePathRule> rules = ruleRepo.findBySiteIdOrderByIdAsc(site.getId());
        if (rules.isEmpty()) {
            return Set.of();
        }

        Set<EvalResult> results = new LinkedHashSet<>();
        for (SitePathRule rule : rules) {
            if (SitePathRule.isFailing(rule) && rule.getConsecutiveFailures() >= threshold) {
                results.add(new EvalResult(
                        rule.getPath(),
                        AlertStatus.ABNORMAL,
                        formatFailure(rule)));
            }
        }
        return results;
    }

    /// 配置读取容错：失败时退回默认值
    private int resolveThreshold(Site site) {
        Integer override = site.getConsecutiveFailuresBeforeAlert();
        if (override != null) {
            return override;
        }
        try {
            var cfg = configService.getOrDefault(
                    ConfigKey.CONSECUTIVE_FAILURES_BEFORE_ALERT,
                    new ConsecutiveFailureConfig());
            return cfg.getConsecutiveFailuresBeforeAlertOrDefault();
        } catch (RuntimeException e) {
            return ConsecutiveFailureConfig.defaultValue();
        }
    }

    private String formatFailure(SitePathRule rule) {
        // KEYWORD 模式：消息聚焦于期望文本与命中情况，HTTP 状态码已无意义
        if (rule.getCheckType() == PathCheckType.KEYWORD) {
            if (rule.getLastTextMatched() == null) {
                // 探测本身失败/超时，body 尚未拿到，展示错误原因 + 期望关键字
                return String.format("路径 %s 探测失败（%s），期望包含「%s」",
                        rule.getPath(),
                        rule.getLastErrorMessage() != null ? rule.getLastErrorMessage() : "结果缺失",
                        rule.getExpectedText());
            }
            if (rule.getLastTextMatched()) {
                // body 命中关键字：isFailing 不会放 true 进来，但方法自身逻辑闭环——
                // 命中即正常，不应出现在告警消息里，降级走通用文案。
                return String.format("路径 %s 检测异常", rule.getPath());
            }
            // body 已拿到但未命中关键字
            return String.format("路径 %s 未包含期望文本「%s」",
                    rule.getPath(), rule.getExpectedText());
        }
        // HTTP_STATUS：以下现有逻辑不变
        Integer got = rule.getLastHttpStatus();
        Integer expected = rule.getExpectedHttpStatus();
        if (got == null) {
            if (rule.getLastErrorMessage() != null) {
                return String.format("路径 %s 探测失败（%s），期望 %d",
                        rule.getPath(), rule.getLastErrorMessage(), expected);
            }
            if (rule.getLastCheckedAt() == null) {
                return String.format("路径 %s 尚未探测（期望 %d）",
                        rule.getPath(), expected);
            }
            return String.format("路径 %s 探测结果缺失（期望 %d）",
                    rule.getPath(), expected);
        }
        return String.format("路径 %s 返回 %d，期望 %d",
                rule.getPath(), got, expected);
    }
}