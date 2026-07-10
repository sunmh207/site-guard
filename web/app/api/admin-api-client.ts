/**
 * 管理后台 API 客户端工具
 * 统一处理管理后台的 API 请求
 */

import { useApi, $api } from './api-client'

/**
 * 管理后台 API 基础路径
 */
const ADMIN_API_BASE = '/api/v1/admin'

/**
 * 管理后台专用的 useFetch 包装器
 * 自动添加认证头和 /api/v1/admin 前缀
 */
export function useAdminApi<T = any>(path: string | (() => string), options?: Parameters<typeof useFetch<T>>[1]) {
  const url = typeof path === 'function'
      ? () => {
        const result = path()
        return result ? `${ADMIN_API_BASE}${result}` : null
      }
      : `${ADMIN_API_BASE}${path}`

  return useApi<T>(url, options)
}

/**
 * 管理后台专用的 $fetch 包装器
 * 自动添加认证头和 /api/v1/admin 前缀
 */
export function $adminApi<T>(path: string, options?: Parameters<typeof $fetch>[1]): Promise<T> {
  return $api<T>(`${ADMIN_API_BASE}${path}`, options)
}
