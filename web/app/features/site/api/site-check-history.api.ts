/// 站点探测历史 API 客户端。
///
/// 路径风格：与 site-path-rule 一致，嵌套在 /site/{siteId}/... 下，
/// 走 $adminApi 一次性拉取（useFetch 模式不适合 dialog/slideover 的"打开即取"语义）。
import { $adminApi } from '~/api/admin-api-client'
import type { SiteCheckHistoryDto } from '../types/check-history.dto'

export const adminSiteCheckHistoryApi = {
  /// 站点最近 N 条探测历史（默认 30，服务端有 30 的硬上限）。
  /// GET /site/{siteId}/history/get?limit=...
  listRecent(siteId: number, limit = 30): Promise<SiteCheckHistoryDto[]> {
    return $adminApi<SiteCheckHistoryDto[]>(`/site/${siteId}/history/get`, {
      query: { limit },
    })
  },
}