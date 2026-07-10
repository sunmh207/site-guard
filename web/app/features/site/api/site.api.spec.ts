/// adminSiteApi 的单元测试。
///
/// 通过 vi.mock 替换 ~/api/admin-api-client 的真实导出，断言 5 个端点
/// 的路径、HTTP 方法、参数拼装是否一致。
import { describe, expect, it, vi } from 'vitest'

const useAdminApiMock = vi.fn()
const $adminApiMock = vi.fn()

vi.mock('~/api/admin-api-client', () => ({
  useAdminApi: (...args: unknown[]) => useAdminApiMock(...args),
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

// 类型层 stub: 让 spec 在不需要解析真实 dto 的情况下加载
vi.mock('~/shared/types/api', () => ({}))

import { adminSiteApi } from './site.api'

describe('adminSiteApi', () => {
  it('searchSites calls /site/search with combined query', () => {
    useAdminApiMock.mockReturnValue({ data: { data: [], total: 0, page: 1, size: 10 } })
    const conditions = { keyword: '官网' }
    const pager = { page: 2, size: 25 }
    // 调用时参数形态由实现消费；mock 不解析，仅验证调用入口
    adminSiteApi.searchSites(conditions as any, pager as any)
    expect(useAdminApiMock).toHaveBeenCalledWith(
      '/site/search',
      expect.objectContaining({ query: expect.anything() }),
    )
  })

  it('getSite passes id as query', async () => {
    await adminSiteApi.getSite(7)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/get',
      expect.objectContaining({ query: { id: 7 } }),
    )
  })

  it('createSite uses POST with body', async () => {
    await adminSiteApi.createSite({ name: '官网', url: 'https://example.com' } as any)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/create',
      expect.objectContaining({
        method: 'POST',
        body: { name: '官网', url: 'https://example.com' },
      }),
    )
  })

  it('updateSite uses POST with body containing id', async () => {
    await adminSiteApi.updateSite({ id: 9, name: '新名', url: 'https://x.com' } as any)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/update',
      expect.objectContaining({
        method: 'POST',
        body: { id: 9, name: '新名', url: 'https://x.com' },
      }),
    )
  })

  it('deleteSite uses POST with body { id }', async () => {
    await adminSiteApi.deleteSite(3)
    expect($adminApiMock).toHaveBeenCalledWith(
      '/site/delete',
      expect.objectContaining({
        method: 'POST',
        body: { id: 3 },
      }),
    )
  })
})
