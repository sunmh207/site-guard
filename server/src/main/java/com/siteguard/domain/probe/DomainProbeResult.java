package com.siteguard.domain.probe;

/// 单次域名查询的最终结果。
///
/// 字段语义：
/// - domainExpiresAt：成功时为到期日毫秒时间戳；查询失败 / 不支持 / 隐私保护 时为 null
/// - 不抛任何异常：调用方看到 null 即可视为失败
public record DomainProbeResult(Long domainExpiresAt) {

    /// 成功时构造
    public static DomainProbeResult ok(long domainExpiresAt) {
        return new DomainProbeResult(domainExpiresAt);
    }

    /// 失败时构造（任意阶段失败：网络 / 解析 / 不支持 / 限速）
    public static DomainProbeResult failed() {
        return new DomainProbeResult(null);
    }
}