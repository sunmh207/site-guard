import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the underlying $adminApi / useAdminApi helpers (correct path: '~/api/admin-api-client')
const $adminApiMock = vi.fn()
const useAdminApiMock = vi.fn()

vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
  useAdminApi: (...args: unknown[]) => useAdminApiMock(...args),
}))

// 类型层 stub: 让 spec 在不需要解析真实 dto 的情况下加载
vi.mock('~/shared/types/api', () => ({}))

import { adminSiteStatsApi } from '~/features/site/api/site-stats.api'

describe('adminSiteStatsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getDashboard calls useAdminApi with /site/stats/dashboard', () => {
    adminSiteStatsApi.getDashboard()

    expect(useAdminApiMock).toHaveBeenCalledWith('/site/stats/dashboard')
    expect(useAdminApiMock).toHaveBeenCalledTimes(1)
  })
})
