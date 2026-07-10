/// 站点模块 API 客户端。
///
/// 路径风格借鉴 edusoho-lms：仅使用 GET 与 POST，按动作命名（search / get / create / update / delete）。
/// useAdminApi / $adminApi 由 ~/api/admin-api-client 提供，会自动加上 /api/v1/admin 前缀与 JWT 头。
import type { UnwrapRef } from 'vue'
import { computed } from 'vue'
import { useAdminApi, $adminApi } from '~/api/admin-api-client'
import type { Pager } from '~/shared/composables/useSearchPagination'
import type { PagerPayload, StatusResult } from '~/shared/types/api'
import type {
  SiteCreateParams,
  SiteDto,
  SiteSearchParams,
  SiteUpdateParams,
} from '../types/site.dto'

export const adminSiteApi = {
  /// 分页搜索（GET /site/search）
  async searchSites(
    conditions: UnwrapRef<SiteSearchParams>,
    pager: UnwrapRef<Pager>,
  ) {
    return await useAdminApi<PagerPayload<SiteDto>>('/site/search', {
      // 把搜索条件与分页合并到 query，computed 让响应式变化自动重发请求
      query: computed(() => ({ ...conditions, ...pager })),
    })
  },

  /// 详情（GET /site/get?id=...）
  async getSite(id: number): Promise<SiteDto> {
    return await $adminApi<SiteDto>('/site/get', {
      query: { id },
    })
  },

  /// 创建（POST /site/create）
  async createSite(params: SiteCreateParams): Promise<SiteDto> {
    return await $adminApi<SiteDto>('/site/create', {
      method: 'POST',
      body: params,
    })
  },

  /// 更新（POST /site/update，body 含 id）
  async updateSite(params: SiteUpdateParams): Promise<SiteDto> {
    return await $adminApi<SiteDto>('/site/update', {
      method: 'POST',
      body: params,
    })
  },

  /// 删除（POST /site/delete，body { id }）
  async deleteSite(id: number): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/site/delete', {
      method: 'POST',
      body: { id },
    })
  },

  /// 设置站点暂停状态（POST /site/set-paused）
  async setPaused(id: number, paused: boolean): Promise<SiteDto> {
    return await $adminApi<SiteDto>('/site/set-paused', {
      method: 'POST',
      body: { id, paused },
    })
  },

  /// 批量移动站点到目标分类（拖拽落点）
  async moveSites(siteIds: number[], categoryId: number): Promise<StatusResult<number>> {
    return await $adminApi<StatusResult<number>>('/site/move', {
      method: 'POST',
      body: { siteIds, categoryId },
    })
  },
}
