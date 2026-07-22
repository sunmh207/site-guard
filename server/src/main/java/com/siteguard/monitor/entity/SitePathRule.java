package com.siteguard.monitor.entity;

import com.siteguard.common.persistent.BaseEntity;
import com.siteguard.monitor.probe.PathCheckType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/// 站点自定义子路由检测规则。
///
/// 字段分两组：
/// - 配置：path / expectedHttpStatus（用户编辑）
/// - 探测状态：lastCheckedAt / lastHttpStatus / lastErrorMessage（PathCheckProbe 写入，PathCheckAlertDefinition 读取）
///
/// 与 Site 是一对多关系，但**没有外键**（项目惯例：cascade 在 service 层维护）。
@Entity
@Table(name = "site_path_rule")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SitePathRule extends BaseEntity {

    /// 所属站点 id
    @Column(name = "site_id", nullable = false)
    private Long siteId;

    /// 相对路径（必须以 / 开头）
    @Column(nullable = false, length = 512)
    private String path;

    /// 判定类型：HTTP_STATUS（默认）/ KEYWORD。
    /// @Enumerated(STRING) 按枚举 name() 存字符串（HTTP_STATUS / KEYWORD），与项目 @Enumerated 惯例一致；
    /// 前端 TS DTO 也约定传大写枚举名，MapStruct 按枚举名自动映射 DTO↔Entity。
    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 16)
    private PathCheckType checkType = PathCheckType.HTTP_STATUS;

    /// 期望包含的关键字；check_type=KEYWORD 时必填。
    @Column(name = "expected_text", length = 255)
    private String expectedText;

    /// 最近一次是否命中关键字；null 表示未探测/探测失败。
    @Column(name = "last_text_matched")
    private Boolean lastTextMatched;

    /// 期望 HTTP 状态码
    @Column(name = "expected_http_status", nullable = false)
    private Integer expectedHttpStatus;

    /// 最近一次探测时间戳（毫秒）
    @Column(name = "last_checked_at")
    private Long lastCheckedAt;

    /// 最近一次实际 HTTP 状态码；连接失败/超时时为 null
    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    /// 最近一次探测的可读错误摘要；成功时为 null
    @Column(name = "last_error_message", length = 512)
    private String lastErrorMessage;

    /// 连续探测失败次数；probe 层维护；探测成功归零
    /// 默认 0；首次失败后为 1；持续失败累加
    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    /// 探测失败判定（probe 层与 detector 层共用）：
    /// - KEYWORD：last_text_matched 为 null（探测失败）或 false（未命中）→ 失败
    /// - HTTP_STATUS：last_http_status 为 null 或 != expected_http_status → 失败
    ///
    /// 注意：Integer 是引用类型，== 比较的是对象身份而非数值；超过 127 的状态码（如 500）
    /// 每次自动装箱会得到不同实例，必须用 equals 避免误判。
    public static boolean isFailing(SitePathRule rule) {
        if (rule.getCheckType() == PathCheckType.KEYWORD) {
            return rule.getLastTextMatched() == null || !rule.getLastTextMatched();
        }
        Integer got = rule.getLastHttpStatus();
        if (got == null) {
            return true;
        }
        return !got.equals(rule.getExpectedHttpStatus());
    }
}
