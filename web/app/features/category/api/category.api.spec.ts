/// adminCategoryApi 单测：reorder 部分。
///
/// 验证：
///   - 调 POST /category/reorder
///   - body 序列化为 { items: [{id, seq}, ...] }
///   - 透传 StatusResult
import { beforeEach, describe, expect, it, vi } from 'vitest'

const $adminApiMock = vi.fn()

vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

import { adminCategoryApi } from './category.api'

describe('adminCategoryApi.reorder', () => {
  beforeEach(() => {
    $adminApiMock.mockReset().mockResolvedValue({ code: 'Ok', data: null, message: null })
  })

  it('POST /category/reorder，body 为 { items }', async () => {
    const items = [
      { id: 1, seq: 100 },
      { id: 2, seq: 200 },
      { id: 3, seq: 300 },
    ]
    await adminCategoryApi.reorder(items)
    expect($adminApiMock).toHaveBeenCalledTimes(1)
    expect($adminApiMock).toHaveBeenCalledWith('/category/reorder', {
      method: 'POST',
      body: { items },
    })
  })

  it('空数组也能发送（后端 @NotEmpty 会拒绝，但客户端透传）', async () => {
    await adminCategoryApi.reorder([])
    expect($adminApiMock).toHaveBeenCalledWith('/category/reorder', {
      method: 'POST',
      body: { items: [] },
    })
  })

  it('透传 StatusResult 给调用方', async () => {
    const ok = { code: 'Ok', data: null, message: null }
    $adminApiMock.mockReset().mockResolvedValueOnce(ok)
    const result = await adminCategoryApi.reorder([{ id: 1, seq: 100 }])
    expect(result).toEqual(ok)
  })
})
