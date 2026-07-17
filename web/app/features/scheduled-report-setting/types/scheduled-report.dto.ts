/// 定时发送报告配置 dto。
///
/// 后端：系统配置（key=scheduled_report）的 JSON 值结构，
///       与 java ConfigKey.SCHEDULED_REPORT（ScheduledReportConfig）一一对应。
///
/// 注意：通用 ConfigResponse<T> 已上移至 ~/shared/types/api，
/// 本模块与 notification-setting / open-dashboard-setting 共享该类型。

/// 定时报告配置值：enabled 开关 + time 每日发送时刻（"HH:mm" 24 小时制）。
export interface ScheduledReportConfig {
  /// 是否启用；null/false → 调度器跳过（默认关闭）。
  enabled?: boolean
  /// 每日发送时刻，"HH:mm"（24 小时制）；null → 默认 "08:00"。
  time?: string
}
