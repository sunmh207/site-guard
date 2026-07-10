package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.site.entity.SiteStatus;
import com.siteguard.system.config.ConsecutiveFailureConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;

/// 可用性检测器：把 Site.availabilityStatus 映射为 bucket，受连续失败阈值约束。
///
/// bucket 语义：
/// - UP    : 在线，NORMAL
/// - DOWN  : 离线，ABNORMAL（需 counter >= threshold 才进入真值集合）
///
/// UNKNOWN（尚未探测完成）保守视为 DOWN 异常；
/// counter < threshold 时返回空集（"该站点在此 kind 上无事件可发"）。
///
/// 阈值由 ConfigService 每次 eval 时实时读取：用户在 /api/v1/admin/config 改完即生效。
/// 配置缺失/字段为 null → 降级到 ConsecutiveFailureConfig.defaultValue() = 1。
@Component
public class AvailabilityAlertDefinition implements AlertDefinition {

    private static final String BUCKET_UP = "UP";
    private static final String BUCKET_DOWN = "DOWN";

    private final ConfigService configService;

    public AvailabilityAlertDefinition(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public AlertKind kind() {
        return AlertKind.AVAILABILITY;
    }

    @Override
    public Set<EvalResult> eval(Site site, Clock clock) {
        SiteStatus status = site.getAvailabilityStatus();
        if (status == SiteStatus.UP) {
            // UP 状态不参与阈值判定：已恢复就直接归位 NORMAL，避免恢复瞬间 counter 还没清零被误判
            return Set.of(new EvalResult(BUCKET_UP, AlertStatus.NORMAL, "可用性已恢复"));
        }
        // DOWN 或 UNKNOWN 都归为 DOWN 档（保守监控）
        int threshold = resolveThreshold(site);
        if (site.getConsecutiveAvailabilityFailures() < threshold) {
            // 累计中未达阈值：本 tick 不参与判定
            return Set.of();
        }
        // message 仅描述"发生了什么"，站点上下文（名称/域名）由 NotificationListener 拼接
        return Set.of(new EvalResult(BUCKET_DOWN, AlertStatus.ABNORMAL, "当前不可用"));
    }

    /// 配置读取容错：失败时退回默认值，避免单次配置错误导致告警系统整体失效
    ///
    /// 优先级：站点级 override > 全局配置 > ConsecutiveFailureConfig.defaultValue()
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
}