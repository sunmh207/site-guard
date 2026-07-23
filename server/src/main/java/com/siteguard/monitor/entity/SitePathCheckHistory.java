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

///
/// 子路由（路径规则）检测历史。
///
/// 镜像 SiteCheckHistory：每次 PathCheckProbe 探测一条路径规则后写一行，
/// 供前端 slideover 展示某条路径的历史状态变化，便于故障排查。
///
/// 与 SiteCheckHistory 的差异：
/// - 多 rule_id + path：定位到具体路径规则
/// - 多 text_matched：KEYWORD 模式的关键字命中情况
/// - 不继承 BaseEntity：所有时间字段都来自业务含义（探测发生时间 checkedAt），
///   框架的 createdAt/updatedAt 在这里没有意义。
@Entity
@Table(name = "site_path_check_history")
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class SitePathCheckHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /// 冗余自 rule.path，规则删除后历史仍可读。
    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "checked_at", nullable = false)
    private Long checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CheckStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    /// 仅 KEYWORD 模式有效；HTTP_STATUS 模式与探测失败时为 null。
    @Column(name = "text_matched")
    private Boolean textMatched;

    @Column(name = "error_message", length = 512)
    private String errorMessage;
}
