/**
 * 系统信息 Composable
 */

import type { SystemInfoDTO } from '~/shared/types/dtos'
import { useAdminApi } from '~/api/admin-api-client'

/**
 * 获取系统信息
 */
export function useSystemInfo() {
  return useAdminApi<SystemInfoDTO>('/system-info', {
    method: 'GET',
  })
}
