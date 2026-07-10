package com.siteguard.monitor.alert.detection;

import com.siteguard.monitor.alert.AlertDefinition;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;

/// 域名到期检测器：固定 15 天阈值，无用户配置。
///
/// 与 CertExpiryAlertDefinition 行为类似但简化：
/// 域名到期不像证书那么紧迫，单档预警已足够；不做多档边沿。
@Component
public class DomainExpiryAlertDefinition implements AlertDefinition {

    private static final long DAY_MS = 86_400_000L;
    private static final int WARNING_DAYS = 15;
    private static final String BUCKET_EXPIRED = "EXPIRED";
    private static final String BUCKET_NORMAL = "NORMAL";

    @Override
    public AlertKind kind() {
        return AlertKind.DOMAIN_EXPIRING;
    }

    @Override
    public Set<EvalResult> eval(Site site, Clock clock) {
        Long expiresAt = site.getDomainExpiresAt();
        if (expiresAt == null) {
            return Set.of();
        }
        long now = clock.millis();
        long remainMs = expiresAt - now;

        if (remainMs < 0) {
            long overdueDays = -remainMs / DAY_MS;
            return Set.of(new EvalResult(BUCKET_EXPIRED, AlertStatus.ABNORMAL,
                    "域名已过期 " + overdueDays + " 天"));
        }
        if (remainMs < WARNING_DAYS * DAY_MS) {
            long remainDays = remainMs / DAY_MS;
            return Set.of(new EvalResult("W" + WARNING_DAYS, AlertStatus.ABNORMAL,
                    "域名将于 " + remainDays + " 天后过期"));
        }
        return Set.of(new EvalResult(BUCKET_NORMAL, AlertStatus.NORMAL, "域名有效期已恢复"));
    }
}