/// 全局路由守卫：除公开白名单外，access token 缺失或过期一律跳登录页
///
/// 公开白名单：
///   - /login：登录页自身永远放行（避免自己跳自己的 no-op）
///   - /open/**：公开大屏等无需鉴权的内网页面
///
/// 设计取舍：
///   - 跳登录后不携带 redirect 参数；useAuth.login() 固定回仪表盘
///   - 守卫不清理 useState('auth:user') 与 localStorage 中的 token，
///     由下一次登录覆盖；保守做法避免误清正在用的会话
///   - 公开白名单用列表维护，未来加新公开页（如 status page）只需追加一行
import { getAccessToken, isTokenExpired } from '~/shared/utils/tokenStorage'
import { ROUTES } from '~/shared/constants/routes'

/// 公开路径前缀列表：这些路径下放行所有访问，不做 token 校验
const PUBLIC_PATH_PREFIXES = ['/open'] as const

function isPublicPath(path: string): boolean {
  if (path === ROUTES.LOGIN) return true
  return PUBLIC_PATH_PREFIXES.some(p => path === p || path.startsWith(p + '/'))
}

export default defineNuxtRouteMiddleware((to) => {
  if (isPublicPath(to.path)) return

  if (!getAccessToken() || isTokenExpired()) {
    return navigateTo(ROUTES.LOGIN)
  }
})
