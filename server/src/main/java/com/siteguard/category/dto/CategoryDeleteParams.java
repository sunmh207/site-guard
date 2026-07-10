package com.siteguard.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/// 删除分类参数
///
/// 删除分类时，必须提供一个 fallbackId：被删分类下（含后代）的所有站点会迁入此分类。
/// 同一事务内完成站点迁移与分类删除，保证一致性。
@Data
@Schema(description = "删除分类参数")
public class CategoryDeleteParams {

    /// 待删除分类 ID
    @NotNull
    @Schema(description = "待删除分类 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /// 被删分类下站点迁入的目标分类 ID
    @NotNull
    @Schema(description = "站点迁入的目标分类 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fallbackId;
}