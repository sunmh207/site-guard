package com.siteguard.site.dto;

import com.siteguard.site.entity.SiteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// 站点搜索参数（用于列表筛选，不含分页字段）
@Data
@Schema(description = "站点搜索参数")
public class SiteSearchParams {

    /// 名称关键字，模糊匹配
    @Schema(description = "按名称模糊搜索", example = "官网")
    private String keyword;

    /// 可用性状态精确匹配，null/不传表示不过滤
    @Schema(description = "按可用性状态过滤", example = "UP")
    private SiteStatus availabilityStatus;

    @Schema(description = "按分类过滤（自动包含该分类的所有后代）", example = "2")
    private Long categoryId;
}