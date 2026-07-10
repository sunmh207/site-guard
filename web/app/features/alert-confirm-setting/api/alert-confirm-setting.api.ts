/// 连续失败阈值设置 API 客户端。
///
/// 路径风格与 notification-setting.api 保持一致：仅 GET/POST，按动作命名。
/// useAdminApi / $adminApi 由 ~/api/admin-api-client 提供，
/// 自动加上 /api/v1/admin 前缀与 JWT 头。
import { $adminApi } from '~/api/admin-api-client'
import type { StatusResult } from '~/shared/types/api'
import type { AlertConfirmConfigResponse } from '../types/alert-confirm-setting.dto'

const KEY = 'consecutive_failures_before_alert'

export const alertConfirmSettingApi = {
  /// 读取连续失败阈值配置；404 → 未设置（返回 data: null，调用方走默认）。
  ///
  /// 后端把响应包在 StatusResult 里（{ code, data, message }），所以泛型参数
  /// 必须写 StatusResult<ConfigResponse<...>>；调用方取数要走 res.data.value。
  async getConfig(): Promise<StatusResult<AlertConfirmConfigResponse | null>> {
    return await $adminApi<StatusResult<AlertConfirmConfigResponse | null>>('/config/get', {
      query: { key: KEY },
    }).catch(err => {
      /// 404 → 未设置（走默认）
      if (err?.statusCode === 404 || err?.response?.status === 404) {
        return { code: '0', data: null, message: '' }
      }
      throw err
    })
  },

  /// 保存连续失败阈值（覆盖写）
  async updateConfig(value: { consecutiveFailuresBeforeAlert: number }): Promise<StatusResult<AlertConfirmConfigResponse>> {
    return await $adminApi<StatusResult<AlertConfirmConfigResponse>>('/config/set', {
      method: 'POST',
      body: { key: KEY, value },
    })
  },

  /// 删除连续失败阈值配置（回退到默认值）
  async deleteConfig(): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/config/delete', {
      method: 'POST',
      body: { key: KEY },
    })
  },
}
