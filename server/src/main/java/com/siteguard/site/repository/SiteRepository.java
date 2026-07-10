package com.siteguard.site.repository;

import com.siteguard.site.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/// 站点数据访问层
///
/// 继承 [JpaRepository] 提供基础 CRUD，
/// 继承 [QuerydslPredicateExecutor] 提供 Querydsl Predicate 检索能力。
public interface SiteRepository extends JpaRepository<Site, Long>, QuerydslPredicateExecutor<Site> {

    /// 按 name 判断是否存在
    boolean existsByName(String name);

    /// 按 url 判断是否存在
    boolean existsByUrl(String url);

    /// 按 name 判断是否存在且 id 不等于给定的 id（用于更新时排除自己）
    boolean existsByNameAndIdNot(String name, Long id);

    /// 按 url 判断是否存在且 id 不等于给定的 id（用于更新时排除自己）
    boolean existsByUrlAndIdNot(String url, Long id);

    /// 按 paused 统计站点数（保留备用：批量迁移等场景可能用到；dashboard 已不再使用）
    long countByPaused(boolean paused);

    /// 批量把 site.category_id 从 from 改成 to
    ///
    /// 用于分类删除/合并时把目标分类下（含后代）的所有站点迁入 fallback 分类。
    /// 返回受影响行数（供调用方验证/日志）。
    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.categoryId = :to WHERE s.categoryId = :from")
    int updateCategoryIdBulk(@Param("from") Long from, @Param("to") Long to);

    /// 按 ID 列表把 site.category_id 改成目标分类
    ///
    /// 用于前端拖拽：选中若干站点拖到目标分类时一次 UPDATE 完成批量迁移。
    /// 返回受影响行数（供调用方验证/日志）。
    @Modifying
    @Transactional
    @Query("UPDATE Site s SET s.categoryId = :categoryId WHERE s.id IN :ids")
    int updateCategoryIdBulkByIds(@Param("ids") List<Long> ids, @Param("categoryId") Long categoryId);
}