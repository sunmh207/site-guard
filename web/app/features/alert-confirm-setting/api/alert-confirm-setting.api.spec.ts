/// alertConfirmSettingApi 的 mock 测试：验证每个方法调用了正确的路径/方法/参数。
///
/// 测试约定（与 notification-setting.api.spec.ts 保持一致）：
///   - $adminApi 通过 vi.mock 工厂包装函数延迟访问，规避 hoisting 报错
///   - 每次 beforeEach 重置 mock，避免测试间污染
///   - 测试不关心 404 fallback 的语义（那是组件层的事），只验证 200 成功路径上的请求格式
import { describe, it, expect, vi, beforeEach } from 'vitest'

const $adminApiMock = vi.fn()

// vi.mock 工厂会被提升到文件顶部，不能直接引用尚未初始化的变量；
// 这里用包装函数把对 mock 的访问延迟到调用时刻，规避 hoisting 报错。
vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

import { alertConfirmSettingApi } from '~/features/alert-confirm-setting/api/alert-confirm-setting.api'

describe('alertConfirmSettingApi', () => {
  beforeEach(() => {
    $adminApiMock.mockReset()
    $adminApiMock.mockResolvedValue({ data: {} })
  })

  it('getConfig 走 GET query {key=consecutive_failures_before_alert}', async () => {
    await alertConfirmSettingApi.getConfig()

    expect($adminApiMock).toHaveBeenCalledWith('/config/get', {
      query: { key: 'consecutive_failures_before_alert' },
    })
  })

  it('updateConfig 走 POST + body {key, value}', async () => {
    await alertConfirmSettingApi.updateConfig({ consecutiveFailuresBeforeAlert: 5 })

    expect($adminApiMock).toHaveBeenCalledWith('/config/set', {
      method: 'POST',
      body: { key: 'consecutive_failures_before_alert', value: { consecutiveFailuresBeforeAlert: 5 } },
    })
  })

  it('deleteConfig 走 POST + body {key}', async () => {
    await alertConfirmSettingApi.deleteConfig()

    expect($adminApiMock).toHaveBeenCalledWith('/config/delete', {
      method: 'POST',
      body: { key: 'consecutive_failures_before_alert' },
    })
  })

  it('getConfig 在 404 时返回 {code:"0", data:null, message:""}（未设置态）', async () => {
    const err404 = Object.assign(new Error('NOT_FOUND'), { statusCode: 404 })
    $adminApiMock.mockRejectedValueOnce(err404)

    const res = await alertConfirmSettingApi.getConfig()

    expect(res).toEqual({ code: '0', data: null, message: '' })
  })

  it('getConfig 在非 404 错误时透传抛出', async () => {
    $adminApiMock.mockRejectedValueOnce(new Error('server boom'))

    await expect(alertConfirmSettingApi.getConfig()).rejects.toThrow('server boom')
  })
})
