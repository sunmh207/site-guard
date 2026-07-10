package com.siteguard.site.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/// 更新站点参数（含 ID 标识）
@Data
@Schema(description = "更新站点参数")
public class SiteUpdateParams {

    /// 待更新的站点 ID
    @NotNull
    @Schema(description = "站点 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

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

    @Schema(description = "所属分类 ID；不传则不改")
    private Long categoryId;

    /// 站点级连续失败阈值覆盖；null 表示沿用全局默认；必须 ≥ 1；不传则不改
    @Min(1)
    @Schema(description = "站点级连续失败阈值覆盖；null 表示沿用全局默认", nullable = true, minimum = "1")
    private Integer consecutiveFailuresBeforeAlert;
}