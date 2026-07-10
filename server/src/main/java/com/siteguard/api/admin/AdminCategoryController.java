package com.siteguard.api.admin;

import com.siteguard.category.dto.CategoryCreateParams;
import com.siteguard.category.dto.CategoryDeleteParams;
import com.siteguard.category.dto.CategoryTreeNode;
import com.siteguard.category.dto.CategoryUpdateParams;
import com.siteguard.category.service.CategoryService;
import com.siteguard.common.dto.StatusResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// 分类管理后台接口
///
/// 路径：/api/v1/admin/category
/// 设计风格：仅使用 GET 与 POST，按动作命名（tree / create / update / delete）。
/// 与 AdminSiteController 保持一致。
@RestController
@RequestMapping("/api/v1/admin/category")
@RequiredArgsConstructor
@Tag(name = "分类管理", description = "后台站点分类管理相关接口")
public class AdminCategoryController {

    private final CategoryService service;

    /// 整树查询（空库自动 seed 默认分类）
    @Operation(summary = "获取分类树")
    @GetMapping("/tree")
    public List<CategoryTreeNode> tree() {
        return service.tree();
    }

    /// 创建分类
    @Operation(summary = "创建分类")
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryTreeNode create(@Valid @RequestBody CategoryCreateParams params) {
        return service.create(params);
    }

    /// 更新分类（重命名 / 移动 / 排序）
    @Operation(summary = "更新分类")
    @PostMapping("/update")
    public CategoryTreeNode update(@Valid @RequestBody CategoryUpdateParams params) {
        return service.update(params);
    }

    /// 删除分类（事务内把站点迁到 fallbackId）
    @Operation(summary = "删除分类（站点迁入 fallback）")
    @PostMapping("/delete")
    public StatusResult<Void> delete(@Valid @RequestBody CategoryDeleteParams params) {
        service.delete(params.getId(), params.getFallbackId());
        return StatusResult.ok();
    }
}