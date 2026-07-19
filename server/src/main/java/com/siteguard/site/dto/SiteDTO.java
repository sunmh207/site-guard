package com.siteguard.site.dto;

import com.siteguard.site.entity.MaintenanceStatus;
import com.siteguard.site.entity.SiteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// 站点信息 DTO（用于接口返回）
@Data
@Schema(description = "站点信息")
public class SiteDTO {

    @Schema(description = "站点 ID", example = "1")
    private Long id;

    @Schema(description = "站点名称", example = "官网首页")
    private String name;

    @Schema(description = "站点 URL", example = "https://example.com")
    private String url;

    @Schema(description = "可用性状态", example = "UP")
    private SiteStatus availabilityStatus;

    @Schema(description = "上次检测时间戳（毫秒）", example = "1751299200000")
    private Long lastCheckedAt;

    @Schema(description = "证书到期时间戳（毫秒）", example = "1765603200000")
    private Long certificateExpiresAt;

    @Schema(description = "域名到期时间戳（毫秒）", example = "1765603200000")
    private Long domainExpiresAt;

    @Schema(description = "证书签发机构", example = "Let's Encrypt")
    private String certificateIssuer;

    @Schema(description = "是否暂停监控", example = "false")
    private boolean paused;

    @Schema(description = "创建时间戳（毫秒）", example = "1751299200000")
    private Long createdAt;

    @Schema(description = "更新时间戳（毫秒）", example = "1751299200000")
    private Long updatedAt;

    @Schema(description = "所属分类 ID", example = "1")
    private Long categoryId;

    @Schema(description = "所属分类名称", example = "浙江")
    private String categoryName;

    @Schema(description = "所属分类完整路径", example = "默认分类 / 浙江 / 杭州")
    private String categoryPath;

    /// 站点级连续失败阈值覆盖；null 表示沿用全局默认
    @Schema(description = "站点级连续失败阈值覆盖；null 表示沿用全局默认", nullable = true)
    private Integer consecutiveFailuresBeforeAlert;

    /// 是否放过证书链不完整（PKIX path building failed）。默认 false。
    @Schema(description = "是否放过证书链不完整；strict 握手失败且 trust-all 重连成功时生效", example = "false")
    private boolean certForgiveChainIncomplete;

    /// 是否放过域名不匹配（SAN/CN 与 host 不一致）。默认 false。
    @Schema(description = "是否放过域名不匹配；strict 握手失败且 trust-all 重连成功时生效", example = "false")
    private boolean certForgiveDomainMismatch;

    /// 是否放过自签证书（issuer DN == subject DN）。默认 false。
    @Schema(description = "是否放过自签证书；strict 握手失败且 trust-all 重连成功时生效", example = "false")
    private boolean certForgiveSelfSigned;

    /// 站点运维时段运行态(只读计算字段)。
    /// - NONE:      站点未启用运维时段(默认)
    /// - ACTIVE:    站点已配置运维时段,且此刻正落在窗口内(运维进行中)
    /// - SCHEDULED: 站点已配置运维时段,但此刻不在窗口内(计划态)
    @Schema(description = "运维时段运行态:NONE=未启用,ACTIVE=进行中,SCHEDULED=计划态",
            nullable = true, example = "ACTIVE")
    private MaintenanceStatus maintenanceStatus;

    /// 运维时段原始 JSON 对象,仅编辑回显/提交用。
    /// 若站点未启用则为 null。前端表单回显到此值,配合 maintenanceStatus 展示"运维中"徽标。
    @Schema(description = "运维时段原始 JSON;例 {\"start\":\"22:00\",\"end\":\"08:00\"}",
            nullable = true,
            example = "{\"start\":\"22:00\",\"end\":\"08:00\"}")
    private String maintenance;
}