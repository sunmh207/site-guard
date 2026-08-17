package com.siteguard.monitor.repository;

import com.siteguard.monitor.entity.SitePathCheckHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

///
/// 子路由检测历史 Repository。
///
/// 镜像 SiteCheckHistoryRepository 的设计分层：
/// - delete* 由 cleanup job 与站点删除场景调用
/// - findByRuleIdOrderByCheckedAtDesc 支撑 slideover"某条路径的历史"展示
public interface SitePathCheckHistoryRepository extends JpaRepository<SitePathCheckHistory, Long> {

    /// 单批删除指定时间之前的历史（清理任务使用），避免将待删除记录加载为实体。
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from site_path_check_history where checked_at < :threshold limit :batchSize", nativeQuery = true)
    int deleteBatchByCheckedAtLessThan(@Param("threshold") long threshold,
                                       @Param("batchSize") int batchSize);

    /// 删除指定站点的所有历史（站点删除时使用）。
    long deleteBySiteId(long siteId);

    /// 某条路径规则的最近 N 条探测历史（按 checked_at 倒序）。
    /// 方法名派生：走 idx_rule_checked (rule_id, checked_at) 索引。
    /// 用于 slideover"子路由检测历史"展示。
    List<SitePathCheckHistory> findByRuleIdOrderByCheckedAtDesc(long ruleId, Pageable pageable);
}
