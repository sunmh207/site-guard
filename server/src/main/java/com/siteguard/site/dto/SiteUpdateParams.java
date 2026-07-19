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

    /// 是否放过证书链不完整（PKIX path building failed）。strict 握手失败且 trust-all 重连成功时生效。
    /// null = 不传则不改（PATCH 语义）；true/false = 显式设置。
    @Schema(description = "是否放过证书链不完整；null 表示不修改", nullable = true, example = "false")
    private Boolean certForgiveChainIncomplete;

    /// 是否放过域名不匹配（证书 SAN/CN 与 host 不一致）。strict 握手失败且 trust-all 重连成功时生效。
    /// null = 不传则不改（PATCH 语义）；true/false = 显式设置。
    @Schema(description = "是否放行域名不匹配；null 表示不修改", nullable = true, example = "false")
    private Boolean certForgiveDomainMismatch;

    /// 是否放过自签证书（issuer DN == subject DN）。strict 握手失败且 trust-all 重连成功时生效。
    /// null = 不传则不改（PATCH 语义）；true/false = 显式设置。
    @Schema(description = "是否放行自签证书；null 表示不修改", nullable = true, example = "false")
    private Boolean certForgiveSelfSigned;

    /// 运维时段 JSON 对象字符串,每日该时段跳过探测/告警(与 paused 同质,按时间表自动开关)。
    /// 结构: {"start":"22:00","end":"08:00","days":["MON","TUE","WED","THU","FRI"]}
    /// - start / end 必填,格式 "HH:mm";start ＞ end 视为跨日窗口
    /// - days 可选,MON..SUN 子集;不传 = 全周(最常见场景)
    /// - 省略 或 传 null = 不动当前配置(PATCH 语义)
    /// 非法 JSON / 语义非法(start==end、非法天数等) → 后端校验 400
    @Schema(description = "运维时段,JSON 对象;例 {\"start\":\"22:00\",\"end\":\"08:00\"};null/省略=不修改",
            nullable = true,
            example = "{\"start\":\"22:00\",\"end\":\"08:00\"}")
    private String maintenance;

    /// PATCH 语义下的"取消运维时段"信号。
    /// 当 true 时,把站点 maintenance 字段清空(= 未启用,站点恢复 24h 监控)。
    /// false 或省略 = 不动;配合 maintenance 字段,实现"修改 / 取消 / 不动"三态语义。
    @Schema(description = "是否取消运维时段(true=清空 maintenance 字段);false/省略=不修改当前配置",
            nullable = true, example = "false")
    private Boolean unsetMaintenance;
}