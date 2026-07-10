/**
 * 专用于 Token 刷新的 API 客户端
 * 不使用自动刷新逻辑，避免循环调用
 */

/**
 * 刷新 Token 专用的 $fetch 包装器
 * 不会触发自动刷新逻辑，避免无限循环
 */
export function $refreshApi<T>(url: string, options?: Parameters<typeof $fetch>[1]): Promise<T> {
  return $fetch<T>(url, {
    ...options,
    // 不添加 Authorization header，因为 refreshToken 在 Cookie 中
    credentials: 'include', // 确保发送 Cookie
  })
}
