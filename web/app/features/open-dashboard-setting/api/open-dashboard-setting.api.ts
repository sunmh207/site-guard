/// 公开大屏设置 API 客户端。
///
/// 复用 admin 配置接口（POST /config/set），key=open_dashboard。
/// 路径风格与 notificationSettingApi 保持一致：仅 GET/POST，按动作命名。
import { $adminApi } from '~/api/admin-api-client'
import type { StatusResult } from '~/shared/types/api'
import type { ConfigResponse, OpenDashboardEnabled } from '../types/open-dashboard-setting.dto'

const KEY = 'open_dashboard'

/// 由于本系统沿用通用 ConfigResponse<T> 而 T 用 boolean，
/// 这里直接复用 shared 通用泛型（与 notification-setting 一致）。
export const openDashboardSettingApi = {
  /// 读取公开大屏开关；不存在抛 404（调用方负责 catch → false 默认关闭态）。
  async getEnabled(): Promise<StatusResult<ConfigResponse<OpenDashboardEnabled>>> {
    return await $adminApi<StatusResult<ConfigResponse<OpenDashboardEnabled>>>('/config/get', {
      query: { key: KEY },
    })
  },

  /// 设置公开大屏开关（覆盖写）。立刻生效——下一次 GET /api/v1/open/site/stats/dashboard
  /// 请求就会拿到新值；admin 调用成功后立即拿到新的 StatusResult 响应。
  async setEnabled(value: boolean): Promise<StatusResult<ConfigResponse<OpenDashboardEnabled>>> {
    return await $adminApi<StatusResult<ConfigResponse<OpenDashboardEnabled>>>('/config/set', {
      method: 'POST',
      body: { key: KEY, value },
    })
  },

  /// 删除公开大屏配置（回退到默认关闭态）。
  /// 实际使用频率低；删除与"设为 false"语义上有差异：删除后数据库无 key，
  /// getOrDefault 走到 fallback=false，效果等同，但审计上更干净。
  async deleteEnabled(): Promise<StatusResult<void>> {
    return await $adminApi<StatusResult<void>>('/config/delete', {
      method: 'POST',
      body: { key: KEY },
    })
  },
}
