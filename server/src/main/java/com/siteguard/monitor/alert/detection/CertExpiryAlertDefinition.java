package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import com.siteguard.system.config.CertAlertConfig;
import com.siteguard.system.enums.ConfigKey;
import com.siteguard.system.service.ConfigService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Arrays;
import java.util.Set;

/// 证书到期检测器：把剩余天数映射到档位。
///
/// bucket 语义：
/// - EXPIRED : 证书已过期
/// - W{天数} : 距过期 < N 天（命中的最大 N，对应"最近一次跨档"档位）
/// - NORMAL  : 全部阈值都未触发
///
/// 阈值由 ConfigService 每次 eval 时实时读取：用户在 /api/v1/admin/config 改完即生效。
/// 配置缺失/为空 → 降级到默认 [14, 7, 3]。
@Component
public class CertExpiryAlertDefinition implements AlertDefinition {

    private static final long DAY_MS = 86_400_000L;
    private static final String BUCKET_EXPIRED = "EXPIRED";
    private static final String BUCKET_NORMAL = "NORMAL";

    private final ConfigService configService;

    public CertExpiryAlertDefinition(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public AlertKind kind() {
        return AlertKind.CERT_EXPIRY;
    }

    @Override
    public Set<EvalResult> eval(Site site, Clock clock) {
        Long expiresAt = site.getCertificateExpiresAt();
        if (expiresAt == null) {
            return Set.of();
        }
        long now = clock.millis();
        long remainMs = expiresAt - now;

        if (remainMs < 0) {
            long overdueDays = -remainMs / DAY_MS;
            String message = "证书已过期 " + overdueDays + " 天";
            return Set.of(new EvalResult(BUCKET_EXPIRED, AlertStatus.ABNORMAL, message));
        }

        int[] days = resolveWarningDays();
        // 升序遍历：先检查最小阈值（最严重档位），命中即返回
        // 例：剩余 5 天、阈值 [14,7,3] → W7（不是 W14）
        Arrays.sort(days);
        for (int d : days) {
            if (remainMs < d * DAY_MS) {
                long remainDays = remainMs / DAY_MS;
                String message = "证书将于 " + remainDays + " 天后过期";
                return Set.of(new EvalResult("W" + d, AlertStatus.ABNORMAL, message));
            }
        }
        return Set.of(new EvalResult(BUCKET_NORMAL, AlertStatus.NORMAL, "证书有效期已恢复"));
    }

    /// 配置读取容错：失败时退回默认阈值，避免单次配置错误导致告警系统整体失效
    private int[] resolveWarningDays() {
        try {
            var cfg = configService.getOrDefault(ConfigKey.CERT_ALERT,
                    CertAlertConfig.builder().warningDays(CertAlertConfig.defaultWarningDays()).build());
            return cfg.getWarningDaysOrDefault();
        } catch (RuntimeException e) {
            return CertAlertConfig.defaultWarningDays();
        }
    }
}