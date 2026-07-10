package com.siteguard.monitor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/// 站点一次探测的历史记录。
///
/// - 不继承 BaseEntity：所有时间字段都来自业务含义（探测发生时间 checkedAt），
///   框架的 createdAt/updatedAt 在这里没有意义。
/// - 站点删除时由 [com.siteguard.site.service.impl.SiteServiceImpl] 同步清理本表对应 site_id 的记录。
@Entity
@Table(name = "site_check_history")
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SiteCheckHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "checked_at", nullable = false)
    private Long checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CheckStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_ms")
    private Integer responseMs;

    @Column(name = "error_message", length = 512)
    private String errorMessage;
}
