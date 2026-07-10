package com.siteguard.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/// 创建分类参数
@Data
@Schema(description = "创建分类参数")
public class CategoryCreateParams {

    /// 父分类 ID；null = 创建为根分类（spec §5.2）。
    /// 注意：parentId 不能加 @NotNull，否则根分类创建会被校验拒绝 —— 与业务语义矛盾。
    @Schema(description = "父分类 ID；null = 创建根分类", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long parentId;

    /// 分类名称（1-64 字符，同一父下唯一）
    @NotBlank
    @Length(min = 1, max = 64)
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64)
    private String name;
}