package com.siteguard.monitor.dashboard;

import com.siteguard.monitor.alert.AlertDTO;
import com.siteguard.monitor.alert.AlertKind;
import com.siteguard.monitor.alert.AlertStatus;
import com.siteguard.site.entity.Site;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/// 证书/域名到期告警源：从 site 快照派生。
///
/// 规则：
/// - expiresAt < now                                → ABNORMAL  ("已过期 N 天")
/// - 0 ≤ expiresAt − now < WARNING_DAYS 天          → ABNORMAL  ("将于 N 天后过期")
/// - 超出阈值（≥ 阈值天数）                          → 不输出
/// - expiresAt 为 null                              → 不输出
///
/// 阈值说明：本期使用固定 15 天作为兜底；
/// 后续阶段会通过 ConfigKey.CERT_ALERT / CertAlertConfig 让用户可配阈值与多档（14/7/3），
/// 届时本类通过 ConfigService 注入替换此常量。
@Component
public class ExpirationAlertSource implements DashboardAlertSource {

    private static final long DAY_MS = 86_400_000L;
    private static final int WARNING_DAYS = 15;

    private final Clock clock;

    public ExpirationAlertSource(Clock clock) {
        this.clock = clock;
    }

    @Override
    public AlertKind kind() {
        // 一个 source 输出两种 kind（cert + domain 都是到期），但 kind() 只允许一种枚举值。
        // 实现里同时 emit 两种；聚合器不依赖这个 kind() 来过滤（因为不同事件不同 kind），
        // 这里返回 CERT_EXPIRY 作为代表，便于日志识别这个 source 来自到期检测。
        return AlertKind.CERT_EXPIRY;
    }

    @Override
    public List<AlertDTO> load(List<Site> allSites) {
        long now = clock.millis();
        long warningMs = (long) WARNING_DAYS * DAY_MS;
        var out = new ArrayList<AlertDTO>();
        for (var s : allSites) {
            // 用户主动暂停的站点不产生到期告警：暂停状态下不再打扰；
            // 站点快照里的过期时间是已知事实，恢复监控后会自然再次触发告警。
            if (s.isPaused()) continue;
            addIfExpiring(out, s, s.getCertificateExpiresAt(), AlertKind.CERT_EXPIRY, "证书", now, warningMs);
            addIfExpiring(out, s, s.getDomainExpiresAt(),      AlertKind.DOMAIN_EXPIRING, "域名", now, warningMs);
        }
        return out;
    }

    /// 单条到期检测：根据 expiresAt 与 now 的关系决定是否追加告警。
    /// detectedAt 优先取 site.lastCheckedAt，未检测过则退化为 now。
    private void addIfExpiring(List<AlertDTO> out, Site s, Long expiresAt, AlertKind kind,
                               String label, long now, long warningMs) {
        if (expiresAt == null) {
            return;
        }
        long remainingMs = expiresAt - now;
        // 整日截断：非 day-boundary 的 expiresAt 会被截断（如 3天23小时 → 3 天）；
        // 当前上游写入的 expiresAt 均为 day-boundary，行为等价于精确计算。
        long daysLeft = remainingMs / DAY_MS;
        long detectedAt = s.getLastCheckedAt() != null ? s.getLastCheckedAt() : now;
        String message;
        AlertStatus status;
        if (expiresAt < now) {
            status = AlertStatus.ABNORMAL;
            message = label + "已过期 " + (-daysLeft) + " 天";
        } else if (remainingMs < warningMs) {
            status = AlertStatus.ABNORMAL;
            message = label + "将于 " + daysLeft + " 天后过期";
        } else {
            return;
        }
        out.add(new AlertDTO(
                s.getId(),
                s.getName(),
                s.getUrl(),
                kind,
                status,
                detectedAt,
                message));
    }
}
