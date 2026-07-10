package com.siteguard.site.entity;

import com.siteguard.common.persistent.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
}