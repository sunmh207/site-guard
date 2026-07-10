package com.siteguard.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/// 更新分类参数
///
/// 所有字段可选；null/未传字段保持不变。systemFlag=true 的节点禁止修改 parentId。
@Data
@Schema(description = "更新分类参数")
public class CategoryUpdateParams {

    /// 待更新分类 ID
    @NotNull
    @Schema(description = "分类 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /// 新名称（1-64 字符）；不传则不改
    @Length(min = 1, max = 64)
    @Schema(description = "新名称；不传则不改")
    private String name;

    /// 新父分类 ID；不传则不改；systemFlag 节点禁止改此字段
    @Schema(description = "新父分类 ID；不传则不改；systemFlag 节点禁止改此字段")
    private Long parentId;

    /// 新排序权重；不传则不改
    @Schema(description = "新排序权重；不传则不改")
    private Integer seq;
}