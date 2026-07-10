package com.siteguard.category.service;

import com.siteguard.category.dto.CategoryCreateParams;
import com.siteguard.category.dto.CategoryTreeNode;
import com.siteguard.category.dto.CategoryUpdateParams;
import java.util.List;
import java.util.Set;

/// 分类业务服务
///
/// 负责分类树的 CRUD、唯一性校验、防环检测、systemFlag 保护、默认分类 seed 等业务逻辑。
/// Controller 不直接持有 Repository，所有数据访问都由本服务封装。
public interface CategoryService {

    /// 整树查询。若库为空，则自动 seed 一组默认分类后返回。
    List<CategoryTreeNode> tree();

    /// 创建分类。同一父下名称冲突时抛 AppException (CONFLICT)；parentId 不存在时抛 (NOT_FOUND)。
    CategoryTreeNode create(CategoryCreateParams params);

    /// 更新分类。含防环检测、systemFlag 保护（禁止改 parentId）、同父下唯一性校验。
    /// ID 不存在时抛 AppException (NOT_FOUND)，目标父节点会形成环时抛 (BAD_REQUEST)。
    CategoryTreeNode update(CategoryUpdateParams params);

    /// 删除分类。在事务内把该分类及其所有后代上的站点迁入 fallbackId，然后删除节点树。
    /// systemFlag 节点禁止删除；ID 不存在或 fallbackId 不存在时抛 AppException。
    void delete(Long id, Long fallbackId);

    /// 返回 id 自身及其所有后代的 ID 集合（含 id 自身）。空集合表示 id 不存在。
    Set<Long> descendantIds(Long id);

    /// 获取"默认分类"ID，用于 SiteService 创建/导入站点时的兜底目标。
    /// 默认分类由系统 seed，systemFlag=true。始终保证存在。
    Long defaultCategoryId();
}