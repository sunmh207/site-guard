/// openDashboardSettingApi 的 mock 测试：验证每个方法调用了正确的路径/方法/参数。
import { describe, it, expect, vi, beforeEach } from 'vitest'

const $adminApiMock = vi.fn()

// vi.mock 工厂会被提升到文件顶部，不能直接引用尚未初始化的变量；
// 这里用包装函数把对 mock 的访问延迟到调用时刻，规避 hoisting 报错。
vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

import { openDashboardSettingApi } from '~/features/open-dashboard-setting/api/open-dashboard-setting.api'

describe('openDashboardSettingApi', () => {
  beforeEach(() => {
    $adminApiMock.mockReset()
    $adminApiMock.mockResolvedValue({ data: {} })
  })

  it('getEnabled 走 GET query {key=open_dashboard}', async () => {
    await openDashboardSettingApi.getEnabled()

    expect($adminApiMock).toHaveBeenCalledWith('/config/get', {
      query: { key: 'open_dashboard' },
    })
  })

  it('setEnabled(true) 走 POST + body {key, value:true}', async () => {
    await openDashboardSettingApi.setEnabled(true)

    expect($adminApiMock).toHaveBeenCalledWith('/config/set', {
      method: 'POST',
      body: { key: 'open_dashboard', value: true },
    })
  })

  it('setEnabled(false) 走 POST + body {key, value:false}', async () => {
    await openDashboardSettingApi.setEnabled(false)

    expect($adminApiMock).toHaveBeenCalledWith('/config/set', {
      method: 'POST',
      body: { key: 'open_dashboard', value: false },
    })
  })

  it('deleteEnabled 走 POST + body {key}', async () => {
    await openDashboardSettingApi.deleteEnabled()

    expect($adminApiMock).toHaveBeenCalledWith('/config/delete', {
      method: 'POST',
      body: { key: 'open_dashboard' },
    })
  })
})
