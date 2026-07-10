/// 公开大屏设置模块的类型定义。
///
/// 后端：系统配置（key=open_dashboard）的 JSON 值结构，
///       与 java ConfigKey.OPEN_DASHBOARD（Boolean）一一对应。

/// 通用配置响应结构：与 notification-setting.dto 中 ConfigResponse 同型。
/// 这里没有引入跨模块引用，遵循「每个 feature 自描述」原则——未来如果
/// notification-setting 或 cert-alert 还需要复用，可以提到 shared/types。
export interface ConfigResponse<T = unknown> {
  key: string
  value: T
  updatedAt: number
}

/// 开关值：true=已开启；false 或不存在=已关闭（默认）。
export type OpenDashboardEnabled = boolean

/// 公开大屏完整配置：当前仅一个布尔字段，未来若加 token/白名单等可在此处扩展。
export interface OpenDashboardConfig {
  enabled: boolean
}
