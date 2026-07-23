/// 子路由检测历史 API 客户端。
///
/// 路径风格：与 site-path-rule 一致，嵌套在 /site/{siteId}/pathRules/... 下，
/// 走 $adminApi 一次性拉取（slideover 的"打开即取"语义）。
import { $adminApi } from '~/api/admin-api-client'
import type { SitePathCheckHistoryDto } from '../types/site-path-check-history.dto'

export const adminSitePathCheckHistoryApi = {
  /// 某条路径规则的最近 N 条探测历史（默认 30，服务端有 30 的硬上限）。
  /// GET /site/pathRule/history/get?ruleId=...&limit=...
  listRecent(ruleId: number, limit = 30): Promise<SitePathCheckHistoryDto[]> {
    return $adminApi<SitePathCheckHistoryDto[]>(`/site/pathRule/history/get`, {
      query: { ruleId, limit },
    })
  },
}
