package com.siteguard.monitor.service;

import com.siteguard.monitor.dto.DashboardResponse;
import com.siteguard.monitor.dto.SiteCheckHistoryDTO;
import com.siteguard.site.entity.Site;

import java.util.List;

/// 监控相关的服务入口。
///
/// - checkAll: Quartz Job 调用入口，并发探测所有站点
/// - checkOne: 单个站点的探测 + 写历史 + 更新快照；任何步骤的异常都被吞掉
/// - listRecent: 站点最近 N 条探测历史（按 checked_at 倒序），用于列表页 slideover
/// - getDashboard: 仪表盘聚合查询
public interface SiteCheckService {

    /// 并发探测所有站点（按 id 升序保证轮询公平）。
    /// 内部使用虚拟线程，单个站点失败不影响其他站点。
    void checkAll();

    /// 探测单个站点并落地（写历史 + 更新快照）。本方法不抛任何异常。
    void checkOne(Site site);

    /// 仪表盘聚合查询：summary + 统一告警面板（按 severity 排序，最近 CRITICAL 在前）。
    /// 实现由 SiteCheckServiceImpl.getDashboard() 委托给 DashboardAlertAggregationService。
    DashboardResponse getDashboard();

    /// 站点最近 limit 条探测历史（按 checked_at 倒序）。
    /// limit 会在实现层做硬上限钳制，避免外部绕过；站点不存在或无历史时返回空列表。
    List<SiteCheckHistoryDTO> listRecent(long siteId, int limit);
}