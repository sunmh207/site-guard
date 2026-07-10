package com.siteguard.category.repository;

import com.siteguard.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/// 分类数据访问层
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /// 查所有子节点
    List<Category> findAllByParentId(Long parentId);

    /// 查所有（任意 parent）—— 用于 tree 整树加载
    List<Category> findAllByOrderBySeqAscIdAsc();

    /// 同 parent + name 是否存在
    boolean existsByParentIdAndName(Long parentId, String name);

    /// 同 parent + name 是否存在且 id 不等于给定 id（重命名冲突用）
    boolean existsByParentIdAndNameAndIdNot(Long parentId, String name, Long id);

    /// 查系统默认分类
    Optional<Category> findFirstBySystemFlagTrue();
}