/**
 * 路由常量定义
 *
 * 所有页面跳转 / Link / router.push 都应从这里引用，而不是硬编码字符串。
 */

export const ROUTES = {
  // 公共路由
  HOME: '/',
  LOGIN: '/login',

  // 公开路由（无需鉴权、仅内网）
  OPEN: {
    /// 公开大屏 dashboard 入口
    DASHBOARD: '/open/dashboard',
  },

  // 管理后台路由
  ADMIN: {
    INDEX: '/admin',
    DASHBOARD: '/admin/dashboard',
    /// 监控仪表盘入口
    ADMIN_DASHBOARD: '/admin/dashboard',
    /// 站点管理入口
    SITES: '/admin/sites',
    /// 通用设置入口（默认通知 Tab）
    SETTINGS: '/admin/settings',
    /// 通知设置 Tab 深链
    SETTINGS_NOTIFICATION: '/admin/settings?tab=notification',
    /// 显示设置 Tab 深链（公开大屏等 UI 开关）
    SETTINGS_DISPLAY: '/admin/settings?tab=display',
  },
} as const
