/**
 * Token 存储工具
 */

import { STORAGE_KEYS } from '~/shared/constants/config'

/**
 * 保存 Access Token 和过期时间
 */
export function saveAccessToken(token: string, expire?: number): void {
  if (import.meta.client) {
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, token)
    if (expire) {
      localStorage.setItem(STORAGE_KEYS.TOKEN_EXPIRE, expire.toString())
    }
  }
}

/**
 * 获取 Access Token
 */
export function getAccessToken(): string | null {
  if (import.meta.client) {
    return localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  }
  return null
}

/**
 * 获取 Token 过期时间
 */
export function getTokenExpire(): number | null {
  if (import.meta.client) {
    const expire = localStorage.getItem(STORAGE_KEYS.TOKEN_EXPIRE)
    return expire ? parseInt(expire, 10) : null
  }
  return null
}

/**
 * 检查 Token 是否过期
 */
export function isTokenExpired(): boolean {
  const expire = getTokenExpire()
  if (!expire) return true
  
  // 提前 30 秒判定为过期，避免边界情况
  return Date.now() >= expire - 30000
}

/**
 * 移除所有 Token 相关数据
 */
export function removeAccessToken(): void {
  if (import.meta.client) {
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
    localStorage.removeItem(STORAGE_KEYS.TOKEN_EXPIRE)
  }
}

