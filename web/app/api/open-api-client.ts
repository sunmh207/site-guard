/**
 * 管理后台 API 客户端工具
 * 统一处理管理后台的 API 请求
 */

import { useApi, $api } from './api-client'

/**
 * 管理后台 API 基础路径
 */
const OPEN_API_BASE = '/api/v1/open'

/**
 * 管理后台专用的 useFetch 包装器
 * 自动添加认证头和 /api/v1/open 前缀
 */
export function useOpenApi<T = any>(path: string | (() => string), options?: Parameters<typeof useFetch<T>>[1]) {
  const url = typeof path === 'function' ? () => `${OPEN_API_BASE}${path()}` : `${OPEN_API_BASE}${path}`

  return useApi<T>(url, options)
}

/**
 * 管理后台专用的 $fetch 包装器
 * 自动添加认证头和 /api/v1/open 前缀
 */
export function $openApi<T>(path: string, options?: Parameters<typeof $fetch>[1]): Promise<T> {
  return $api<T>(`${OPEN_API_BASE}${path}`, options)
}
