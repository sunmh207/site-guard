package com.siteguard.site.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/// 创建站点参数
@Data
@Schema(description = "创建站点参数")
public class SiteCreateParams {

    /// 站点名称
    @NotBlank
    @Length(min = 1, max = 128)
    @Schema(description = "站点名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128, example = "官网首页")
    private String name;

    /// 站点 URL（必须以 http:// 或 https:// 开头）
    @NotBlank
    @Length(min = 1, max = 512)
    @Pattern(regexp = "^https?://.+", message = "URL 必须以 http:// 或 https:// 开头")
    @Schema(description = "站点 URL", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 512, example = "https://example.com")
    private String url;

    @Schema(description = "所属分类 ID；不传则落入默认分类")
    private Long categoryId;

    /// 站点级连续失败阈值覆盖；null 表示沿用全局默认；必须 ≥ 1
    @Min(1)
    @Schema(description = "站点级连续失败阈值覆盖；null 表示沿用全局默认", nullable = true, minimum = "1")
    private Integer consecutiveFailuresBeforeAlert;

    /// 是否放过证书链不完整（PKIX path building failed）。默认 false。strict 握手失败且 trust-all 重连成功时生效。
    @Schema(description = "是否放过证书链不完整（PKIX path building failed）", defaultValue = "false", example = "false")
    private Boolean certForgiveChainIncomplete;

    /// 是否放过域名不匹配（证书 SAN/CN 与 host 不一致）。默认 false。strict 握手失败且 trust-all 重连成功时生效。
    @Schema(description = "是否放行域名不匹配", defaultValue = "false", example = "false")
    private Boolean certForgiveDomainMismatch;

    /// 是否放过自签证书（issuer DN == subject DN）。默认 false。strict 握手失败且 trust-all 重连成功时生效。
    @Schema(description = "是否放行自签证书", defaultValue = "false", example = "false")
    private Boolean certForgiveSelfSigned;
}