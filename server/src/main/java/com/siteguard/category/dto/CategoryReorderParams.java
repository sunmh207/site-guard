package com.siteguard.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/// 分类顺序调整参数
///
/// 调用方负责计算每个 item 的 seq（前端步长 100：1→100, 2→200, ...），
/// 后端按 items 全量覆盖对应 id 的 seq。items 列表应当是同父级下完整兄弟 ID 列表。
/// id 不存在时抛 AppException(NOT_FOUND)。
@Data
@Schema(description = "分类顺序调整参数")
public class CategoryReorderParams {

    /// 待调整顺序的分类条目
    @NotEmpty
    @Valid
    @Schema(description = "待调整顺序的分类条目", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> items;

    /// 单个条目
    @Data
    @Schema(description = "单个分类条目")
    public static class Item {

        /// 分类 ID
        @NotNull
        @Schema(description = "分类 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        /// 新的排序权重（兄弟间相对顺序，按升序展示）
        @NotNull
        @Min(0)
        @Schema(description = "新的排序权重", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer seq;
    }
}
