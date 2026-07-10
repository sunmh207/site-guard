/// 公开 dashboard API 客户端。
///
/// 走 useOpenApi（不带 Authorization header），访问 /api/v1/open/site/stats/dashboard。
/// 与 adminSiteStatsApi 共用同一份 DTO（DashboardResponse），结构上等价。
///
/// 位置选择：放在 features/site/api/ 与 adminSiteStatsApi 对称，
/// 不另起 features/open-dashboard 模块——DTO 已在 features/site/types/，跨模块引用破坏对称性。
import { useOpenApi } from '~/api/open-api-client'
import type { DashboardResponse } from '~/features/site/types/site-stats.dto'

export const openSiteStatsApi = {
  /// 公开仪表盘聚合（GET /api/v1/open/site/stats/dashboard）
  getDashboard: () => useOpenApi<DashboardResponse>('/site/stats/dashboard'),
}