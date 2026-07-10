/// 站点自定义子路由检测规则 DTO（前后端共享字段名约定）
export interface SitePathRuleDto {
  id: number | null
  siteId: number
  path: string
  expectedHttpStatus: number
  lastCheckedAt: number | null
  lastHttpStatus: number | null
  lastErrorMessage: string | null
  /// 当前是否在告警（site_check_state 存在 (site, PATH_CHECK, path) 行）。
  /// 未探测或恢复后为 null。
  alertingSince: number | null
}

/// 整批 set 请求体
export interface SitePathRuleListRequest {
  siteId: number
  rules: SitePathRuleDto[]
}