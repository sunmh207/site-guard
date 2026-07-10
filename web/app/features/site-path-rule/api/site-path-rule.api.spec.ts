/// adminSitePathRuleApi 的单元测试。
///
/// 模仿 web/app/features/site/api/site.api.spec.ts 的风格：
/// vi.mock 替换 ~/api/admin-api-client，断言 3 个端点的路径、HTTP 方法、参数拼装。
import { describe, expect, it, vi } from 'vitest'

const useAdminApiMock = vi.fn()
const $adminApiMock = vi.fn()

vi.mock('~/api/admin-api-client', () => ({
  useAdminApi: (...args: unknown[]) => useAdminApiMock(...args),
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

vi.mock('~/shared/types/api', () => ({}))

import { adminSitePathRuleApi } from './site-path-rule.api'

describe('adminSitePathRuleApi', () => {
  it('listPathRules calls /site/{siteId}/pathRules/get', async () => {
    await adminSitePathRuleApi.listPathRules(1)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/1/pathRules/get',
      expect.objectContaining({ method: undefined }),
    )
  })

  it('setPathRules uses POST with body', async () => {
    const body = {
      siteId: 1,
      rules: [
        { id: null, siteId: 1, path: '/a', expectedHttpStatus: 200,
          lastCheckedAt: null, lastHttpStatus: null, lastErrorMessage: null, alertingSince: null },
      ],
    }
    await adminSitePathRuleApi.setPathRules(1, body.rules)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/1/pathRules/set',
      expect.objectContaining({
        method: 'POST',
        body: { siteId: 1, rules: body.rules },
      }),
    )
  })

  it('deletePathRule uses POST with body { id }', async () => {
    await adminSitePathRuleApi.deletePathRule(7)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/pathRule/delete',
      expect.objectContaining({
        method: 'POST',
        body: { id: 7 },
      }),
    )
  })
})