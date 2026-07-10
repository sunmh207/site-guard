package com.siteguard.monitor.repository;

import com.siteguard.monitor.entity.SiteCheckHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/// 站点探测历史数据访问层
///
/// 方法分组：
/// - delete* 由 cleanup Job 与站点删除场景调用
/// - avgResponseMsSince 支撑仪表盘"近 1h 平均响应"卡片
/// - findRecentIssues 支撑仪表盘"最近异常"列表
public interface SiteCheckHistoryRepository extends JpaRepository<SiteCheckHistory, Long> {

    /// 删除指定时间之前的所有历史（清理任务使用）
    long deleteByCheckedAtLessThan(long threshold);

    /// 删除指定站点的所有历史（站点删除时使用）
    long deleteBySiteId(long siteId);

    /// 统计指定时间窗口内 UP 探测的平均响应时间（毫秒）。
    /// 走 idx_site_checked 索引。
    @Query("""
            select avg(h.responseMs) from SiteCheckHistory h
            where h.status = com.siteguard.monitor.entity.CheckStatus.UP
              and h.checkedAt >= :since
            """)
    Double avgResponseMsSince(@Param("since") long since);

    /// 查询当前的异常列表：每个 (site_id, status) 仅保留最新一条，按 checked_at 倒序。
    ///
    /// 语义：
    /// - 排除 status = UP 的记录（UP 不是异常）
    /// - 同一站点连续多次同类异常（如 site A TIMEOUT × 3）只展示最新一次，避免列表被重复条目淹没
    /// - 同站点的不同状态（如 site A 同时有 DOWN 和 TIMEOUT）各占一行
    ///
    /// 实现：派生表先按 (site_id, status) 聚合取每组的 MAX(checked_at)，
    /// 再与原表 INNER JOIN 拉回完整行。语义与原 NOT EXISTS 等价——
    /// 并列时间戳的行同样全部保留（JOIN 条件 (site_id, status, checked_at) 完全相等）。
    ///
    /// 索引：派生表走 idx_status_site_checked (status, site_id, checked_at) 触发 loose index scan，
    /// 替代原相关子查询的 O(N·logN)；外层 JOIN 结果集 = 非 UP (site, status) 组数，
    /// ORDER BY checked_at DESC 在小结果集内 filesort，开销可控。
    @Query("""
            select h from SiteCheckHistory h
            join (
                select h2.siteId as siteId, h2.status as status, max(h2.checkedAt) as checkedAt
                from SiteCheckHistory h2
                where h2.status <> com.siteguard.monitor.entity.CheckStatus.UP
                group by h2.siteId, h2.status
            ) t on h.siteId = t.siteId
                 and h.status = t.status
                 and h.checkedAt = t.checkedAt
            order by h.checkedAt desc
            """)
    List<SiteCheckHistory> findRecentIssues(Pageable pageable);

    /// 站点最近 N 条探测历史（按 checked_at 倒序）。
    /// 方法名派生：JpaRepository 会自动生成 `where site_id = ? order by checked_at desc` SQL，
    /// 配合 Pageable.size 限制返回行数。走 idx_site_checked (site_id, checked_at) 索引。
    /// 用于后台"站点详情"页 / 列表页 slideover 的"探测历史"展示。
    List<SiteCheckHistory> findBySiteIdOrderByCheckedAtDesc(long siteId, Pageable pageable);
}