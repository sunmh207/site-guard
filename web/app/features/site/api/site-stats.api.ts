/// 站点监控统计 API 客户端。
///
/// 路径风格与 adminSiteApi 保持一致：基于 useAdminApi 自动加 /api/v1/admin 前缀。
import { useAdminApi } from '~/api/admin-api-client'
import type { DashboardResponse } from '~/features/site/types/site-stats.dto'

export const adminSiteStatsApi = {
  /// 仪表盘聚合（GET /site/stats/dashboard）
  getDashboard: () => useAdminApi<DashboardResponse>('/site/stats/dashboard'),
}
