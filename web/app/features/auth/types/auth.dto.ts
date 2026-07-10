/**
 * Auth 模块 - 后端接口契约 (DTO)
 */

/**
 * 登录请求
 */
export interface LoginDTO {
  username: string
  password: string
}

export interface AuthUserDTO {
  id: number
  username: string
  nickname?: string | null
}

/**
 * 登录响应
 */
export interface FullAuthUserDTO {
  user: AuthUserDTO
  accessToken: string
  accessTokenTtl: number
  refreshToken: string  // 虽然存在 Cookie 中，但后端会返回
  refreshTokenTtl: number
}

/**
 * Token 刷新响应
 */
export interface RefreshTokenResponse {
  user: AuthUserDTO
  accessToken: string
  accessTokenTtl: number
  refreshToken: string
  refreshTokenTtl: number
}
