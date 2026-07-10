package com.siteguard.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/// 分类树节点（嵌套 children）
///
/// 用于整树查询的返回结构。每个节点携带自身信息 + 直接子节点列表，
/// siteCount 统计本节点及其所有后代节点上的站点总数。
@Data
@Schema(description = "分类树节点")
public class CategoryTreeNode {

    /// 分类 ID
    @Schema(description = "分类 ID")
    private Long id;

    /// 父分类 ID；null 表示根分类
    @Schema(description = "父分类 ID；null = 根")
    private Long parentId;

    /// 分类名称
    @Schema(description = "分类名称")
    private String name;

    /// 系统默认分类标记（true = 受保护，不可删）
    @Schema(description = "系统默认分类标记")
    private Boolean systemFlag;

    /// 排序权重（同父节点下升序）
    @Schema(description = "排序权重")
    private Integer seq;

    /// 本节点 + 所有后代节点上的站点数量
    @Schema(description = "本站点 + 所有后代站点的数量")
    private Long siteCount;

    /// 直接子节点列表
    @Schema(description = "子节点")
    private List<CategoryTreeNode> children;
}