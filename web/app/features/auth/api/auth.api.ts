/**
 * Auth 模块 - API 调用
 */
import { $openApi } from '~/api/open-api-client'
import { $refreshApi } from '~/api/refresh-api-client'
import type { LoginDTO, FullAuthUserDTO, RefreshTokenResponse } from '../types/auth.dto'

const OPEN_API_BASE = '/api/v1/open'

export const authApi = {
    /**
     * 登录
     */
    login: (data: LoginDTO) =>
        $openApi<FullAuthUserDTO>('/auth/login', {
            method: 'POST',
            body: data,
        }),

    /**
     * 登出
     */
    logout: () =>
        $openApi<void>('/auth/logout', {
            method: 'POST',
        }),

    /**
     * 刷新 Token
     * 注意：refreshToken 存储在 HttpOnly Cookie 中，会自动发送
     * 使用专用的 API 客户端，避免循环刷新
     */
    refreshToken: () =>
        $refreshApi<RefreshTokenResponse>(`${OPEN_API_BASE}/auth/refresh`, {
            method: 'POST',
        }),
}
