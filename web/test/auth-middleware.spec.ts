/// 全局路由守卫 auth.global 的单测
///
/// 4 个用例：登录页放行 / 无 token 跳登录 / token 过期跳登录 / token 有效放行
///
/// mock 策略：
///   - vi.hoisted 内注入 tokenStorage mock + Nuxt auto-import stub
///     （必须 hoist 到 import 之前；ESM import 提升会先于模块体执行，
///      而 auth.global.ts 在 import 时就会调 defineNuxtRouteMiddleware）
const {
  getAccessTokenMock,
  isTokenExpiredMock,
  navigateToMock,
} = vi.hoisted(() => {
  const getAccessTokenMock = vi.fn()
  const isTokenExpiredMock = vi.fn()
  const navigateToMock = vi.fn()
  // Nuxt auto-import：vitest 下未定义；defineNuxtRouteMiddleware 是恒等包装
  vi.stubGlobal('navigateTo', navigateToMock)
  vi.stubGlobal('defineNuxtRouteMiddleware', (fn: (to: any) => any) => fn)
  return { getAccessTokenMock, isTokenExpiredMock, navigateToMock }
})

vi.mock('~/shared/utils/tokenStorage', () => ({
  getAccessToken: () => getAccessTokenMock(),
  isTokenExpired: () => isTokenExpiredMock(),
}))

import { describe, it, expect, vi, beforeEach } from 'vitest'
import authMiddleware from '~/middleware/auth.global'
import { ROUTES } from '~/shared/constants/routes'

describe('auth.global 中间件', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('登录页自身放行，不调 navigateTo', () => {
    authMiddleware({ path: ROUTES.LOGIN } as any)

    expect(navigateToMock).not.toHaveBeenCalled()
  })

  it('无 token → 跳 /login', () => {
    getAccessTokenMock.mockReturnValue(null)

    authMiddleware({ path: '/admin/dashboard' } as any)

    expect(navigateToMock).toHaveBeenCalledTimes(1)
    expect(navigateToMock).toHaveBeenCalledWith(ROUTES.LOGIN)
  })

  it('token 过期 → 跳 /login', () => {
    getAccessTokenMock.mockReturnValue('fake-token')
    isTokenExpiredMock.mockReturnValue(true)

    authMiddleware({ path: '/admin/dashboard' } as any)

    expect(navigateToMock).toHaveBeenCalledTimes(1)
    expect(navigateToMock).toHaveBeenCalledWith(ROUTES.LOGIN)
  })

  it('token 有效 → 放行，不调 navigateTo', () => {
    getAccessTokenMock.mockReturnValue('fake-token')
    isTokenExpiredMock.mockReturnValue(false)

    authMiddleware({ path: '/admin/dashboard' } as any)

    expect(navigateToMock).not.toHaveBeenCalled()
  })

  it('/open/dashboard 公开路径 → 放行（无 token）', () => {
    getAccessTokenMock.mockReturnValue(null)

    authMiddleware({ path: ROUTES.OPEN.DASHBOARD } as any)

    expect(navigateToMock).not.toHaveBeenCalled()
  })

  it('/open 公开前缀 → 放行', () => {
    getAccessTokenMock.mockReturnValue(null)

    authMiddleware({ path: '/open' } as any)

    expect(navigateToMock).not.toHaveBeenCalled()
  })

  it('/openish（不是前缀）→ 跳 /login', () => {
    getAccessTokenMock.mockReturnValue(null)

    authMiddleware({ path: '/openish' } as any)

    expect(navigateToMock).toHaveBeenCalledTimes(1)
    expect(navigateToMock).toHaveBeenCalledWith(ROUTES.LOGIN)
  })
})
