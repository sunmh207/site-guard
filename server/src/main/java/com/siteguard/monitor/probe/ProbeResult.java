package com.siteguard.monitor.probe;

import com.siteguard.monitor.entity.CheckStatus;

/// 单次探活的最终结果。
///
/// 字段语义：
/// - status 永远非空
/// - httpStatus / responseMs 在 UP / DOWN 时有值，TIMEOUT / ERROR 时为 null
/// - errorMessage 在非 UP 时给出可读摘要，UP 时为 null
/// - certExpiresAt / certIssuer 仅 HTTPS 站点会填，非 HTTPS / 解析失败时为 null
public record ProbeResult(
        CheckStatus status,
        Integer httpStatus,
        Integer responseMs,
        String errorMessage,
        Long certExpiresAt,
        String certIssuer
) {

    public static ProbeResult up(int httpStatus, int responseMs) {
        return new ProbeResult(CheckStatus.UP, httpStatus, responseMs, null, null, null);
    }

    /// HTTP 探活成功 + 抓到证书时的工厂方法
    public static ProbeResult up(int httpStatus, int responseMs, long certExpiresAt, String certIssuer) {
        return new ProbeResult(CheckStatus.UP, httpStatus, responseMs, null, certExpiresAt, certIssuer);
    }

    /// 证书过期专用：status 仍为 ERROR（站点不可用），但携带 certExpiresAt / certIssuer
    /// 让 SiteCheckServiceImpl 能写入这些值，触发 CertExpiryAlertDefinition 的"证书已过期"告警。
    ///
    /// 与其他 error() 不同：过期不是"宕机"，但本系统视过期为 ABNORMAL；lenient 路径永远走本工厂，
    /// 不可被任何 cert_forgive 开关放过——过期判定在分类之前就返回了。
    public static ProbeResult expired(String message, HttpSiteProbe.CertInfo certInfo) {
        Long expiresAt = (certInfo != null) ? certInfo.expiresAt() : null;
        String issuer = (certInfo != null) ? certInfo.issuer() : null;
        return new ProbeResult(CheckStatus.ERROR, null, null,
                "CERT_EXPIRED: " + truncate(message), expiresAt, issuer);
    }

    public static ProbeResult down(int httpStatus, int responseMs) {
        return new ProbeResult(CheckStatus.DOWN, httpStatus, responseMs, null, null, null);
    }

    public static ProbeResult timeout() {
        return new ProbeResult(CheckStatus.TIMEOUT, null, 5000, "request timeout after 5s", null, null);
    }

    public static ProbeResult error(String message) {
        return new ProbeResult(CheckStatus.ERROR, null, null, truncate(message), null, null);
    }

    /// 限制 errorMessage 长度，避免写入超长错误信息导致数据库字段溢出。
    /// 数据库 error_message 字段为 VARCHAR(512)。
    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
