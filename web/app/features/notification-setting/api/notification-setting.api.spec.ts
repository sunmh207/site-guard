/// notificationSettingApi 的 mock 测试：验证每个方法调用了正确的路径/方法/参数。
import { describe, it, expect, vi, beforeEach } from 'vitest'

const $adminApiMock = vi.fn()

// vi.mock 工厂会被提升到文件顶部，不能直接引用尚未初始化的变量；
// 这里用包装函数把对 mock 的访问延迟到调用时刻，规避 hoisting 报错。
vi.mock('~/api/admin-api-client', () => ({
  $adminApi: (...args: unknown[]) => $adminApiMock(...args),
}))

import { notificationSettingApi } from '~/features/notification-setting/api/notification-setting.api'

describe('notificationSettingApi', () => {
  beforeEach(() => {
    $adminApiMock.mockReset()
    $adminApiMock.mockResolvedValue({ data: {} })
  })

  it('getConfig 走 GET query {key=notification}', async () => {
    await notificationSettingApi.getConfig()

    expect($adminApiMock).toHaveBeenCalledWith('/config/get', {
      query: { key: 'notification' },
    })
  })

  it('updateConfig 走 POST + body {key, value}', async () => {
    const cfg = { enabled: true, platform: 'DINGTALK' as const, webhookUrl: 'https://x' }
    await notificationSettingApi.updateConfig(cfg as any)

    expect($adminApiMock).toHaveBeenCalledWith('/config/set', {
      method: 'POST',
      body: { key: 'notification', value: cfg },
    })
  })

  it('deleteConfig 走 POST + body {key}', async () => {
    await notificationSettingApi.deleteConfig()

    expect($adminApiMock).toHaveBeenCalledWith('/config/delete', {
      method: 'POST',
      body: { key: 'notification' },
    })
  })

  it('testWebhook 走 POST + body', async () => {
    const params = { platform: 'DINGTALK' as const, webhookUrl: 'https://x' }
    await notificationSettingApi.testWebhook(params)

    expect($adminApiMock).toHaveBeenCalledWith('/config/test-webhook', {
      method: 'POST',
      body: params,
    })
  })
})
