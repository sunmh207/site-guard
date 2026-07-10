/**
 * Auth 模块 - 认证逻辑
 */

import { authApi } from '../api/auth.api'
import { saveAccessToken, removeAccessToken, getAccessToken } from '~/shared/utils/tokenStorage'
import type { AuthUserDTO, LoginDTO } from '../types/auth.dto'
import { ROUTES } from '~/shared/constants/routes'

export function useAuth() {
  const user = useState<AuthUserDTO | null>('auth:user', () => null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  /**
   * 登录
   */
  const login = async (credentials: LoginDTO) => {
    loading.value = true
    error.value = null

    try {
      const fullAuthUserDTO = await authApi.login(credentials)

      // 保存 accessToken 和过期时间
      saveAccessToken(fullAuthUserDTO.accessToken, Date.now() + fullAuthUserDTO.accessTokenTtl)

      // 保存用户信息
      user.value = fullAuthUserDTO.user

      // 跳转到管理后台
      await navigateTo(ROUTES.ADMIN.DASHBOARD)
      
      return fullAuthUserDTO
    } catch (e) {
      error.value = e as Error
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 登出
   */
  const logout = async () => {
    loading.value = true
    error.value = null

    try {
      await authApi.logout()
    } catch (e) {
      console.error('登出失败:', e)
    } finally {
      // 清除本地数据
      removeAccessToken()
      user.value = null
      loading.value = false
      
      // 跳转到登录页
      await navigateTo(ROUTES.LOGIN)
    }
  }

  /**
   * 检查是否已登录
   */
  const isAuthenticated = computed(() => !!user.value && !!getAccessToken())

  return {
    user: readonly(user),
    loading: readonly(loading),
    error: readonly(error),
    isAuthenticated,
    login,
    logout
  }
}
