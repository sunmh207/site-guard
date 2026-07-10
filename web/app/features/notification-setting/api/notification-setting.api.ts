/// 通知设置 API 客户端。
///
/// 路径风格与 adminSiteApi 保持一致：仅 GET/POST，按动作命名。
/// useAdminApi / $adminApi 由 ~/api/admin-api-client 提供，
/// 自动加上 /api/v1/admin 前缀与 JWT 头。
import { $adminApi } from '~/api/admin-api-client'
import type { StatusResult, ConfigResponse } from '~/shared/types/api'
import type {
  NotificationConfig,
  TestWebhookParams,
  TestWebhookResult,
} from '../types/notification-setting.dto'

const KEY = 'notification'

export const notificationSettingApi = {
  /// 读取通知配置；不存在抛 404（调用方负责 catch → null）。
  ///
  /// 后端把响应包在 StatusResult 里（{ code, data, message }），所以泛型参数
  /// 必须写 StatusResult<ConfigResponse<...>>；调用方取数要走 res.data.value，
  /// 直接拿 res.value 会拿到 undefined（历史 bug：ref → res.value → 表单全空）。
  async getConfig(): Promise<StatusResult<ConfigResponse<NotificationConfig>>> {
    return await $adminApi<StatusResult<ConfigResponse<NotificationConfig>>>('/config/get', {
      query: { key: KEY },
    })
  },

  /// 保存通知配置（覆盖写）
  async updateConfig(value: NotificationConfig): Promise<StatusResult<ConfigResponse<NotificationConfig>>> {
    return await $adminApi<StatusResult<ConfigResponse<NotificationConfig>>>('/config/set', {
      method: 'POST',
      body: { key: KEY, value },
    })
  },

  /// 删除通知配置
  async deleteConfig(): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/config/delete', {
      method: 'POST',
      body: { key: KEY },
    })
  },

  /// 测试 Webhook 联通性（不依赖数据库）
  async testWebhook(params: TestWebhookParams): Promise<StatusResult<TestWebhookResult>> {
    return await $adminApi<StatusResult<TestWebhookResult>>('/config/test-webhook', {
      method: 'POST',
      body: params,
    })
  },
}
