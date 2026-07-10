/**
 * Token 刷新管理器
 * 处理 token 自动刷新逻辑，避免并发刷新问题
 */

import { authApi } from '~/features/auth/api/auth.api'
import { saveAccessToken, removeAccessToken } from './tokenStorage'
import { ROUTES } from '~/shared/constants/routes'

/**
 * 刷新状态管理
 */
let isRefreshing = false
let refreshPromise: Promise<string> | null = null

/**
 * 刷新 Access Token
 * 使用 Promise 缓存确保并发请求时只刷新一次
 */
export async function refreshAccessToken(): Promise<string> {
  // 如果正在刷新，返回现有的 Promise
  if (isRefreshing && refreshPromise) {
    console.log('[Token] 等待现有刷新请求完成...')
    return refreshPromise
  }

  // 开始新的刷新流程
  isRefreshing = true
  console.log('[Token] 开始刷新 Access Token...')

  refreshPromise = (async () => {
    try {
      // 调用刷新接口（refreshToken 在 Cookie 中自动发送）
      const response = await authApi.refreshToken()
      
      // 保存新的 token 和过期时间
      saveAccessToken(response.accessToken, Date.now() + response.accessTokenTtl)
      
      console.log('[Token] Access Token 刷新成功')
      return response.accessToken
    } catch (error: any) {
      console.error('[Token] 刷新失败:', error)
      
      // 刷新失败，清除所有认证信息
      handleRefreshFailure()
      
      throw error
    } finally {
      // 重置刷新状态
      isRefreshing = false
      refreshPromise = null
    }
  })()

  return refreshPromise
}

/**
 * 处理刷新失败
 * 清除所有认证信息并跳转到登录页
 */
function handleRefreshFailure(): void {
  console.log('[Token] Refresh Token 已过期，跳转到登录页')
  
  // 清除本地存储
  removeAccessToken()
  
  // 清除用户状态
  if (import.meta.client) {
    useState('auth:user').value = null
  }
  
  // 跳转到登录页
  navigateTo(ROUTES.LOGIN)
}

/**
 * 重置刷新状态（用于测试或特殊场景）
 */
export function resetRefreshState(): void {
  isRefreshing = false
  refreshPromise = null
}
