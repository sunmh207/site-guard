package com.siteguard.site.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/// 设置站点暂停状态入参
@Data
@Schema(description = "设置站点暂停状态")
public class SitePauseParams {

    /// 待设置状态的站点 ID
    @NotNull
    @Schema(description = "站点 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    /// true=暂停（不再扫描），false=恢复（重新进入扫描队列）
    @NotNull
    @Schema(description = "true=暂停；false=恢复", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean paused;
}
