package com.siteguard.site.entity;

import com.siteguard.common.persistent.BaseEntity;
import com.siteguard.monitor.probe.CertForgive;
import com.siteguard.monitor.probe.CertForgiveType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/// 被监控站点实体
///
/// 存储站点的基本信息和最新一次的可用性/证书/域名快照。
/// 监控维度的字段由未来的探测任务写入，本期不实现探测逻辑。
@Entity
@Table(name = "site")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Site extends BaseEntity {

    /// 站点名称，唯一
    @Column(nullable = false, length = 128, unique = true)
    private String name;

    /// 站点 URL，唯一，必须以 http:// 或 https:// 开头（应用层校验）
    @Column(nullable = false, length = 512, unique = true)
    private String url;

    /// 所属分类 ID（必填，关系由代码维护）
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /// 可用性状态，默认 UNKNOWN
    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", length = 16)
    private SiteStatus availabilityStatus;

    /// 上次检测时间戳（毫秒），未检测为 null
    @Column(name = "last_checked_at")
    private Long lastCheckedAt;

    /// 证书到期时间戳（毫秒），未检测或非 HTTPS 为 null
    @Column(name = "certificate_expires_at")
    private Long certificateExpiresAt;

    /// 域名到期时间戳（毫秒），未检测为 null
    @Column(name = "domain_expires_at")
    private Long domainExpiresAt;

    /// 是否暂停监控（true = 不参与扫描、不计入告警面板）。默认 false。
    @Column(nullable = false)
    private boolean paused;

    /// 证书签发机构（如 Let's Encrypt），未检测为 null
    @Column(name = "certificate_issuer", length = 256)
    private String certificateIssuer;

    /// 站点级连续失败阈值覆盖；null 表示沿用 ConsecutiveFailureConfig 全局默认
    @Column(name = "consecutive_failures_before_alert")
    private Integer consecutiveFailuresBeforeAlert;

    /// 连续 DOWN 探测次数；probe 层维护；UP 探测归零
    /// 默认 0；首次 DOWN 后为 1；持续 DOWN 累加；int 足以承载实际场景
    @Column(name = "consecutive_availability_failures", nullable = false)
    private int consecutiveAvailabilityFailures;

    /// 证书校验失败分级放行集合，JSON 字符串数组。
    ///
    /// 含义：JDK strict 握手失败 + trust-all 重连成功时，仅集合内的失败类型会被"放过"（判 UP）；
    /// 集合外类型与 trust-all 仍失败两种场景都按 ERROR 处理。
    ///
    /// "证书过期" 永远不在此集合中：probe 在校验Validity 阶段直接返回 ERROR，不走本字段判定。
    ///
    /// 合法值（null 表示未设置，等价于空集 = 全不放，对齐 JDK 默认严格校验）：
    /// - null / "[]"                               全不放
    /// - ["chain_incomplete"]                      仅放链不完整
    /// - ["chain_incomplete","domain_mismatch"]    放两种
    /// - ["chain_incomplete","domain_mismatch","self_signed"]  全放（仍不放过期）
    @Column(name = "cert_forgive")
    private String certForgive;

    /// 反序列化后的 view。null/空/解析失败 → 空集（严格兜底）。
    /// 通过 CertForgive.parse 走 CertForgiveType.@JsonCreator，不认识的值静默跳过。
    public Set<CertForgiveType> getCertForgiveTypes() {
        return CertForgive.parse(certForgive);
    }

    /// 三个便捷判定（给 probe 直接调用，描述"这种失败类型是否被放过"）。
    public boolean isForgiveChainIncomplete() {
        return getCertForgiveTypes().contains(CertForgiveType.CHAIN_INCOMPLETE);
    }

    public boolean isForgiveDomainMismatch() {
        return getCertForgiveTypes().contains(CertForgiveType.DOMAIN_MISMATCH);
    }

    public boolean isForgiveSelfSigned() {
        return getCertForgiveTypes().contains(CertForgiveType.SELF_SIGNED);
    }

    /// 任一类型可放行 = 进入 lenient 路径的资格。probe 的快速预检，避免无开关时创建 trust-all client。
    public boolean hasAnyCertForgive() {
        return !getCertForgiveTypes().isEmpty();
    }
}