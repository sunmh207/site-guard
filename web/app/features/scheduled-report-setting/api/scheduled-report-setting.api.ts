/// 定时发送报告 API 客户端。
///
/// 复用 admin 配置接口（GET /config/get + POST /config/set），key=scheduled_report。
/// 路径风格与 open-dashboard-setting / alert-confirm-setting 保持一致：仅 GET/POST，按动作命名。
import { $adminApi } from '~/api/admin-api-client'
import type { StatusResult, ConfigResponse } from '~/shared/types/api'
import type { ScheduledReportConfig } from '../types/scheduled-report.dto'

const KEY = 'scheduled_report'

/// 读取定时报告配置；404 → 未设置（data: null，调用方走默认关闭态）。
/// 注意泛型必须写 StatusResult<ConfigResponse<...>>，调用方取数走 res.data.value。
export const scheduledReportSettingApi = {
  async getConfig(): Promise<StatusResult<ConfigResponse<ScheduledReportConfig> | null>> {
    return await $adminApi<StatusResult<ConfigResponse<ScheduledReportConfig> | null>>('/config/get', {
      query: { key: KEY },
    }).catch(err => {
      /// 404 → 未设置（走默认关闭）
      if (err?.statusCode === 404 || err?.response?.status === 404) {
        return { code: '0', data: null, message: '' }
      }
      throw err
    })
  },

  /// 保存定时报告配置（整体覆盖写：enabled + time 一起提交）。
  async setConfig(value: ScheduledReportConfig): Promise<StatusResult<ConfigResponse<ScheduledReportConfig>>> {
    return await $adminApi<StatusResult<ConfigResponse<ScheduledReportConfig>>>('/config/set', {
      method: 'POST',
      body: { key: KEY, value },
    })
  },

  /// 删除定时报告配置（回退到默认关闭态）。
  async deleteConfig(): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/config/delete', {
      method: 'POST',
      body: { key: KEY },
    })
  },
}
