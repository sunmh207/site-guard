import { beforeEach, describe, expect, it, vi } from 'vitest'

const $openApiMock = vi.fn()
const $adminApiMock = vi.fn()

vi.mock('~/api/open-api-client', () => ({
  $openApi: (...args: unknown[]) => $openApiMock(...args),
}))
vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

import { brandingApi } from './branding.api'

describe('brandingApi', () => {
  beforeEach(() => {
    $openApiMock.mockReset().mockResolvedValue({ data: {} })
    $adminApiMock.mockReset().mockResolvedValue({ data: {} })
  })

  it('公开与管理读取使用各自客户端', async () => {
    await brandingApi.getPublic()
    await brandingApi.getAdmin()

    expect($openApiMock).toHaveBeenCalledWith('/branding/get')
    expect($adminApiMock).toHaveBeenCalledWith('/branding/get')
  })

  it('set 直接传 FormData，不手设 Content-Type', async () => {
    const body = new FormData()
    await brandingApi.set(body)

    expect($adminApiMock).toHaveBeenCalledWith('/branding/set', {
      method: 'POST',
      body,
    })
    expect($adminApiMock.mock.calls[0][1]).not.toHaveProperty('headers')
  })

  it('deleteIcon 使用 POST action 路径', async () => {
    await brandingApi.deleteIcon()
    expect($adminApiMock).toHaveBeenCalledWith('/branding/icon/delete', { method: 'POST' })
  })
})
