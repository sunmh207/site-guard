/// 站点子路由规则 API 客户端。
///
/// 路径风格遵循项目惯例：仅 GET/POST，按动作命名（get/set/delete）。
/// useAdminApi / $adminApi 由 ~/api/admin-api-client 提供，自动加 /api/v1/admin 前缀与 JWT。
import { $adminApi } from '~/api/admin-api-client'
import type { SitePathRuleDto, SitePathRuleListRequest } from '../types/site-path-rule.dto'
import type { StatusResult } from '~/shared/types/api'

export const adminSitePathRuleApi = {
  /// 列出某站点的全部规则
  async listPathRules(siteId: number): Promise<StatusResult<SitePathRuleDto[]>> {
    return await $adminApi<StatusResult<SitePathRuleDto[]>>(
      `/site/${siteId}/pathRules/get`,
      { method: undefined },
    )
  },

  /// 整批覆盖（写操作都用 POST）
  async setPathRules(
    siteId: number,
    rules: SitePathRuleDto[],
  ): Promise<StatusResult<void>> {
    const body: SitePathRuleListRequest = { siteId, rules }
    return await $adminApi<StatusResult<void>>(
      `/site/${siteId}/pathRules/set`,
      { method: 'POST', body },
    )
  },

  /// 按 id 删除单条
  async deletePathRule(id: number): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>(
      '/site/pathRule/delete',
      { method: 'POST', body: { id } },
    )
  },
}