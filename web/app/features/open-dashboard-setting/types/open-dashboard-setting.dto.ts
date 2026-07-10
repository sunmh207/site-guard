/// 公开大屏设置模块的类型定义。
///
/// 后端：系统配置（key=open_dashboard）的 JSON 值结构，
///       与 java ConfigKey.OPEN_DASHBOARD（Boolean）一一对应。
///
/// 注意：通用 ConfigResponse<T> 已上移至 ~/shared/types/api，
/// 本模块与 notification-setting / alert-confirm-setting 共享该类型。

/// 开关值：true=已开启；false 或不存在=已关闭（默认）。
export type OpenDashboardEnabled = boolean

/// 公开大屏完整配置：当前仅一个布尔字段，未来若加 token/白名单等可在此处扩展。
export interface OpenDashboardConfig {
  enabled: boolean
}
