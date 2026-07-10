package com.siteguard.site.dto;

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
}