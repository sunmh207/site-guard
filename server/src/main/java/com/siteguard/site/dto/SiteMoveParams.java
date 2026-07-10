package com.siteguard.site.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/// 站点批量移动到分类（拖拽落点用）
@Data
@Schema(description = "批量移动站点到分类")
public class SiteMoveParams {

    @NotEmpty
    @Schema(description = "待移动的站点 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> siteIds;

    @NotNull
    @Schema(description = "目标分类 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;
}
