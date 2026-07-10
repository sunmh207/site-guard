/// 仪表盘告警种类（与后端 AlertKind 一一对应）
export type AlertKind = 'AVAILABILITY' | 'CERT_EXPIRY' | 'DOMAIN_EXPIRING' | 'PATH_CHECK'

/// 告警状态：NORMAL 表示已恢复，ABNORMAL 表示存在异常
/// 替代旧 AlertSeverity（WARNING/CRITICAL），区分度不足且语义重叠
export type AlertStatus = 'NORMAL' | 'ABNORMAL'

/// 仪表盘汇总（5 张卡片）
///
/// 4 桶 + 总站点数；不变性：healthy + abnormal + pending + paused == totalSites。
/// 优先级（一次只落一个桶）：暂停 > 异常 > 健康 > 待检测。
export interface DashboardSummary {
  /// 全部站点数
  totalSites: number
  /// paused=false ∧ lastCheckedAt!=null ∧ 无 ABNORMAL 告警
  healthyCount: number
  /// paused=false ∧ alerts 中存在 ABNORMAL（availability/cert/domain/path 任一）
  abnormalCount: number
  /// paused=false ∧ lastCheckedAt==null
  pendingCount: number
  /// paused=true
  pausedCount: number
  /// 近 1 小时所有 UP 探测的平均响应时间（毫秒），无样本为 null
  avgResponseMs: number | null
}

/// 仪表盘统一告警条目
/// 字段扁平：人读语义由后端写在 message；前端只展示 + 按 kind 选图标
export interface RecentAlert {
  siteId: number
  siteName: string
  siteUrl: string
  kind: AlertKind
  status: AlertStatus
  /// 探测时间，毫秒戳
  detectedAt: number
  /// 人读告警文本，例如
  /// - "HTTP 500: 连接被拒绝"
  /// - "证书将于 7 天后过期"
  /// - "证书已过期 3 天"
  message: string
}

/// 仪表盘聚合响应
export interface DashboardResponse {
  summary: DashboardSummary
  recentAlerts: RecentAlert[]
}